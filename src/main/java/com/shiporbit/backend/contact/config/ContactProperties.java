package com.shiporbit.backend.contact.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * notification.contact.team-email: inbox that receives "a user has contacted us" emails (env CONTACT_TEAM_EMAIL).
 * Blank -> the team email is skipped (logged as a warning); the user still gets the confirmation.
 */
@ConfigurationProperties(prefix = "notification.contact")
public record ContactProperties(String teamEmail) {

    public boolean hasTeamEmail() {
        return teamEmail != null && !teamEmail.isBlank();
    }
}
