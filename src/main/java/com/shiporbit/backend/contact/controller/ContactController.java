package com.shiporbit.backend.contact.controller;

import com.shiporbit.backend.contact.dto.ContactRequest;
import com.shiporbit.backend.contact.dto.ContactResponse;
import com.shiporbit.backend.contact.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Website contact form. Public (no login). 202 Accepted = saved; emails are sent in the background.
 * Invalid input -> 400 with field errors (GlobalExceptionHandler).
 */
@RestController
@RequestMapping("/api/v1/contact")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping("/email")
    public ResponseEntity<ContactResponse> contactUs(@Valid @RequestBody ContactRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ContactResponse.received(contactService.submit(request)));
    }
}
