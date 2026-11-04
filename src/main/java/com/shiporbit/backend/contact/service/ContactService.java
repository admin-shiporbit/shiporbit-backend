package com.shiporbit.backend.contact.service;

import com.shiporbit.backend.contact.config.ContactProperties;
import com.shiporbit.backend.contact.dto.ContactRequest;
import com.shiporbit.backend.contact.entity.ContactEmailStatus;
import com.shiporbit.backend.contact.entity.ContactRequestEntity;
import com.shiporbit.backend.contact.entity.ContactRequestStatus;
import com.shiporbit.backend.contact.repository.ContactRequestRepository;
import com.shiporbit.backend.notification.channel.EmailChannel;
import com.shiporbit.backend.notification.config.NotificationExecutorConfig;
import com.shiporbit.backend.notification.constants.Channel;
import com.shiporbit.backend.notification.constants.NotificationPurpose;
import com.shiporbit.backend.notification.dto.NotificationRequest;
import com.shiporbit.backend.notification.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Contact form flow:
 * <ol>
 *   <li>submit(): save the request (SO_CONTACT_REQUEST, status RECEIVED) and hand the email work to the
 *       notification executor. Returns at once - the controller answers 202 with the reference id.</li>
 *   <li>Background thread, sendEmails():
 *     <ol type="a">
 *       <li>CONTACT_ACK to the user: "we received your request", with their submitted details.</li>
 *       <li>CONTACT_TEAM to notification.contact.team-email: "a user has contacted us", with all details.</li>
 *       <li>Update the row: per-email status + overall COMPLETED / PARTIAL / FAILED.</li>
 *     </ol>
 *   </li>
 * </ol>
 * Both emails go through EmailChannel, so they are templated (SO_NOTIFICATION_TEMPLATE), logged in
 * SO_NOTIFICATION_LOG and de-duplicated by idempotency key CONTACT:&lt;id&gt;:ACK|TEAM:EMAIL.
 * A failed email never fails the request: the submission is already saved and can be followed up from the table.
 */
@Service
public class ContactService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContactService.class);
    private static final DateTimeFormatter SUBMITTED_AT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a 'IST'").withZone(ZoneId.of("Asia/Kolkata"));
    private static final String EMPTY = "-";
    private static final int MAX_ERROR_LENGTH = 500;

    private final ContactRequestRepository repository;
    private final ObjectProvider<EmailChannel> emailChannel;     // absent when notification.email.enabled=false
    private final ContactProperties properties;
    private final TaskExecutor executor;

    public ContactService(ContactRequestRepository repository,
                          ObjectProvider<EmailChannel> emailChannel,
                          ContactProperties properties,
                          @Qualifier(NotificationExecutorConfig.NOTIFICATION_EXECUTOR) TaskExecutor executor) {
        this.repository = repository;
        this.emailChannel = emailChannel;
        this.properties = properties;
        this.executor = executor;
        if (!properties.hasTeamEmail()) {
            LOGGER.warn("notification.contact.team-email (CONTACT_TEAM_EMAIL) is not set: "
                    + "contact requests will be saved and acknowledged, but the team will not be emailed");
        }
    }

    /** Saves the request and queues the emails. Returns the saved id (reference for the user). */
    public UUID submit(ContactRequest request) {
        ContactRequestEntity saved = repository.save(ContactRequestEntity.from(request));
        LOGGER.info("Contact request {} saved (topic {}), emails queued", saved.getId(), saved.getTopic());
        UUID id = saved.getId();
        // Runs after save() has committed (save is its own transaction), so the worker always finds the row.
        executor.execute(() -> sendEmails(id));
        return id;
    }

    /** Background: sends both emails and records the outcome. Never throws. */
    void sendEmails(UUID id) {
        ContactRequestEntity entity = repository.findById(id).orElse(null);
        if (entity == null) {
            LOGGER.error("Contact request {} not found when sending emails", id);
            return;
        }
        Map<String, String> vars = templateVars(entity);
        StringBuilder errors = new StringBuilder();

        EmailChannel channel = emailChannel.getIfAvailable();
        ContactEmailStatus userStatus;
        ContactEmailStatus teamStatus;
        if (channel == null) {
            LOGGER.warn("Contact request {}: email disabled (notification.email.enabled=false), no emails sent", id);
            userStatus = ContactEmailStatus.SKIPPED;
            teamStatus = ContactEmailStatus.SKIPPED;
            errors.append("email disabled");
        } else {
            userStatus = send(channel, NotificationPurpose.CONTACT_ACK, entity.getEmail(), vars,
                    "CONTACT:" + id + ":ACK:EMAIL", errors);
            if (properties.hasTeamEmail()) {
                teamStatus = send(channel, NotificationPurpose.CONTACT_TEAM, properties.teamEmail().trim(), vars,
                        "CONTACT:" + id + ":TEAM:EMAIL", errors);
            } else {
                teamStatus = ContactEmailStatus.SKIPPED;
            }
        }

        entity.setUserEmailStatus(userStatus);
        entity.setTeamEmailStatus(teamStatus);
        entity.setStatus(overall(userStatus, teamStatus));
        entity.setError(errors.isEmpty() ? null : truncate(errors.toString()));
        repository.save(entity);
        LOGGER.info("Contact request {}: user email {}, team email {} -> {}", id, userStatus, teamStatus, entity.getStatus());
    }

    private ContactEmailStatus send(EmailChannel channel, NotificationPurpose purpose, String to,
                                    Map<String, String> vars, String idempotencyKey, StringBuilder errors) {
        try {
            NotificationResponse response = channel.send(
                    new NotificationRequest(null, to, Channel.EMAIL, purpose, vars, idempotencyKey));
            if (response.isSuccess()) {
                return ContactEmailStatus.SENT;
            }
            addError(errors, purpose, response.error());
        } catch (RuntimeException e) {
            // e.g. missing template / invalid address: must not stop the other email or the status update.
            addError(errors, purpose, e.getMessage());
        }
        LOGGER.warn("Contact email {} to {} failed: {}", purpose, to, errors);
        return ContactEmailStatus.FAILED;
    }

    /** Template variables for both emails. Missing optional fields become "-" (TemplateService rejects nulls). */
    static Map<String, String> templateVars(ContactRequestEntity e) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("referenceId", e.getId().toString());
        vars.put("name", orEmpty(e.getName()));
        vars.put("email", orEmpty(e.getEmail()));
        vars.put("company", orEmpty(e.getCompany()));
        vars.put("phone", orEmpty(e.getPhone()));
        vars.put("topic", orEmpty(e.getTopic()));
        vars.put("volume", orEmpty(e.getVolume()));
        vars.put("website", orEmpty(e.getWebsite()));
        vars.put("message", orEmpty(e.getMessage()));
        vars.put("submittedAt", e.getCreatedAt() == null ? EMPTY : SUBMITTED_AT.format(e.getCreatedAt()));
        return vars;
    }

    static ContactRequestStatus overall(ContactEmailStatus user, ContactEmailStatus team) {
        boolean userOk = user == ContactEmailStatus.SENT;
        boolean teamOk = team == ContactEmailStatus.SENT;
        boolean teamNotNeeded = team == ContactEmailStatus.SKIPPED && user != ContactEmailStatus.SKIPPED;
        if (userOk && (teamOk || teamNotNeeded)) {
            return ContactRequestStatus.COMPLETED;
        }
        if (userOk || teamOk) {
            return ContactRequestStatus.PARTIAL;
        }
        return ContactRequestStatus.FAILED;
    }

    private static void addError(StringBuilder errors, NotificationPurpose purpose, String error) {
        if (!errors.isEmpty()) {
            errors.append("; ");
        }
        errors.append(purpose).append(": ").append(error == null ? "unknown error" : error);
    }

    private static String orEmpty(String s) {
        return s == null || s.isBlank() ? EMPTY : s;
    }

    private static String truncate(String s) {
        return s.length() <= MAX_ERROR_LENGTH ? s : s.substring(0, MAX_ERROR_LENGTH);
    }
}
