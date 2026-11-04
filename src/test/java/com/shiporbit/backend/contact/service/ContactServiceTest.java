package com.shiporbit.backend.contact.service;

import com.shiporbit.backend.contact.config.ContactProperties;
import com.shiporbit.backend.contact.dto.ContactRequest;
import com.shiporbit.backend.contact.entity.ContactEmailStatus;
import com.shiporbit.backend.contact.entity.ContactRequestEntity;
import com.shiporbit.backend.contact.entity.ContactRequestStatus;
import com.shiporbit.backend.contact.repository.ContactRequestRepository;
import com.shiporbit.backend.notification.channel.EmailChannel;
import com.shiporbit.backend.notification.constants.NotificationPurpose;
import com.shiporbit.backend.notification.dto.NotificationRequest;
import com.shiporbit.backend.notification.dto.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContactServiceTest {

    private static final ContactRequest REQUEST = new ContactRequest(" Saurabh ", "User@Example.com ", null, "98765 43210",
            "Pricing", "500/month", "Hello\nneed rates", "https://shop.example");

    @SuppressWarnings("unchecked")
    private ContactService service(EmailChannel channel, String teamEmail, ContactRequestRepository repo) {
        ObjectProvider<EmailChannel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(channel);
        return new ContactService(repo, provider, new ContactProperties(teamEmail), Runnable::run);   // run inline
    }

    /** Last entity passed to save(); findById returns it (an in-memory "table" of one row). */
    private final AtomicReference<ContactRequestEntity> stored = new AtomicReference<>();

    private ContactRequestRepository repo() {
        ContactRequestRepository repo = mock(ContactRequestRepository.class);
        when(repo.save(any())).thenAnswer(inv -> {
            stored.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(repo.findById(any())).thenAnswer(inv -> Optional.ofNullable(stored.get()));
        return repo;
    }

    @Test
    void savesThenSendsAckToUserAndNotificationToTeam() {
        ContactRequestRepository repo = repo();
        EmailChannel channel = mock(EmailChannel.class);
        when(channel.send(any())).thenReturn(NotificationResponse.sent("SMTP", "msg-1"));
        ArgumentCaptor<ContactRequestEntity> saved = ArgumentCaptor.forClass(ContactRequestEntity.class);

        UUID id = service(channel, "team@shiporbit.in", repo).submit(REQUEST);

        assertNotNull(id);
        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(channel, times(2)).send(sent.capture());
        List<NotificationRequest> mails = sent.getAllValues();
        assertEquals(NotificationPurpose.CONTACT_ACK, mails.get(0).purpose());
        assertEquals("user@example.com", mails.get(0).recipient());          // trimmed + lower-cased
        assertEquals("CONTACT:" + id + ":ACK:EMAIL", mails.get(0).idempotencyKey());
        assertEquals(NotificationPurpose.CONTACT_TEAM, mails.get(1).purpose());
        assertEquals("team@shiporbit.in", mails.get(1).recipient());
        assertEquals("-", mails.get(1).vars().get("company"));                 // optional blank -> "-"
        assertEquals("Saurabh", mails.get(1).vars().get("name"));

        verify(repo, times(2)).save(saved.capture());                          // RECEIVED, then final status
        ContactRequestEntity last = saved.getValue();
        assertEquals(ContactRequestStatus.COMPLETED, last.getStatus());
        assertEquals(ContactEmailStatus.SENT, last.getUserEmailStatus());
        assertEquals(ContactEmailStatus.SENT, last.getTeamEmailStatus());
    }

    @Test
    void noTeamEmailConfiguredStillCompletes() {
        ContactRequestRepository repo = repo();
        EmailChannel channel = mock(EmailChannel.class);
        when(channel.send(any())).thenReturn(NotificationResponse.sent("SMTP", "msg-1"));

        service(channel, "", repo).submit(REQUEST);

        verify(channel, times(1)).send(any());
        assertEquals(ContactRequestStatus.COMPLETED, stored.get().getStatus());
        assertEquals(ContactEmailStatus.SKIPPED, stored.get().getTeamEmailStatus());
    }

    @Test
    void emailDisabledSavesRequestAsFailed() {
        ContactRequestRepository repo = repo();

        service(null, "team@shiporbit.in", repo).submit(REQUEST);

        assertEquals(ContactRequestStatus.FAILED, stored.get().getStatus());
        assertEquals("email disabled", stored.get().getError());
    }

    @Test
    void oneEmailFailingIsPartialAndNeverThrows() {
        ContactRequestRepository repo = repo();
        EmailChannel channel = mock(EmailChannel.class);
        when(channel.send(any()))
                .thenReturn(NotificationResponse.sent("SMTP", "msg-1"))
                .thenThrow(new RuntimeException("No active template for purpose=CONTACT_TEAM"));

        service(channel, "team@shiporbit.in", repo).submit(REQUEST);

        assertEquals(ContactRequestStatus.PARTIAL, stored.get().getStatus());
        assertEquals(ContactEmailStatus.FAILED, stored.get().getTeamEmailStatus());
    }

    @Test
    void overallStatusRules() {
        assertEquals(ContactRequestStatus.COMPLETED, ContactService.overall(ContactEmailStatus.SENT, ContactEmailStatus.SENT));
        assertEquals(ContactRequestStatus.COMPLETED, ContactService.overall(ContactEmailStatus.SENT, ContactEmailStatus.SKIPPED));
        assertEquals(ContactRequestStatus.PARTIAL, ContactService.overall(ContactEmailStatus.FAILED, ContactEmailStatus.SENT));
        assertEquals(ContactRequestStatus.FAILED, ContactService.overall(ContactEmailStatus.FAILED, ContactEmailStatus.FAILED));
        assertEquals(ContactRequestStatus.FAILED, ContactService.overall(ContactEmailStatus.SKIPPED, ContactEmailStatus.SKIPPED));
    }

    @Test
    void varsNeverNull() {
        ContactRequestEntity e = ContactRequestEntity.from(new ContactRequest("A", "a@b.co", null, null, null, null, "m", null));
        Map<String, String> vars = ContactService.templateVars(e);
        vars.forEach((k, v) -> assertNotNull(v, k));
        assertEquals("-", vars.get("submittedAt"));   // createdAt is set by Hibernate on insert
    }

}
