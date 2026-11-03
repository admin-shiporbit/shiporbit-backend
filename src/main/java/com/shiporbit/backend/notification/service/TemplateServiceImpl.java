package com.shiporbit.backend.notification.service;

import com.shiporbit.backend.exception.NotificationException;
import com.shiporbit.backend.notification.constants.Channel;
import com.shiporbit.backend.notification.constants.NotificationPurpose;
import com.shiporbit.backend.notification.dto.RenderedMessage;
import com.shiporbit.backend.notification.entity.NotificationTemplate;
import com.shiporbit.backend.notification.repository.NotificationTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders DB templates containing {{placeholder}} tokens. */
@Service
public class TemplateServiceImpl implements TemplateService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

    private final NotificationTemplateRepository templateRepo;

    public TemplateServiceImpl(NotificationTemplateRepository templateRepo) {
        this.templateRepo = templateRepo;
    }

    @Override
    public RenderedMessage render(NotificationPurpose purpose, Channel channel, Map<String, String> vars) {
        NotificationTemplate template = templateRepo.findByPurposeAndChannelAndActiveTrue(purpose, channel)
                .orElseThrow(() -> new NotificationException(
                        "No active template for purpose=" + purpose + ", channel=" + channel));

        // Email bodies are HTML: escape values so user input (e.g. a name) cannot inject markup.
        boolean escapeHtml = channel == Channel.EMAIL;
        String subject = template.getSubject() == null ? null : fill(template.getSubject(), vars, false);
        String body = fill(template.getBody(), vars, escapeHtml);
        return new RenderedMessage(subject, body, template.getDltTemplateId());
    }

    private String fill(String text, Map<String, String> vars, boolean escapeHtml) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = vars.get(key);
            if (value == null) {
                throw new NotificationException("Missing template variable: " + key);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(escapeHtml ? HtmlUtils.htmlEscape(value) : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
