package com.resumeai.service;

import com.resumeai.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Sends transactional email over SMTP. All sends are async and failures are
 * logged (with the action link, so dev environments without a mail server can
 * still complete flows) but never break the calling business operation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    @Async
    public void send(String to, String subject, String htmlBody, String actionLink) {
        if (actionLink != null) {
            log.info("Email '{}' to {} - action link: {}", subject, to, actionLink);
        }
        if (!properties.getMail().isEnabled()) {
            log.info("Mail disabled - skipped sending '{}' to {}", subject, to);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.getMail().getFrom(), properties.getMail().getFromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Sent email '{}' to {}", subject, to);
        } catch (Exception e) {
            log.error("Failed to send email '{}' to {}: {}", subject, to, e.getMessage());
        }
    }

    public String frontendUrl(String path) {
        String base = properties.getFrontendBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    /** Shared branded wrapper for all transactional emails. */
    public String template(String title, String bodyHtml, String buttonText, String buttonUrl) {
        String button = "";
        if (buttonText != null && buttonUrl != null) {
            button = """
                    <table role="presentation" cellpadding="0" cellspacing="0" style="margin:28px auto;">
                      <tr><td style="border-radius:8px;background:#4f46e5;">
                        <a href="%s" style="display:inline-block;padding:13px 32px;color:#ffffff;
                           text-decoration:none;font-weight:600;font-size:15px;">%s</a>
                      </td></tr>
                    </table>
                    <p style="font-size:12px;color:#6b7280;text-align:center;">
                      Or copy this link into your browser:<br/>
                      <a href="%s" style="color:#4f46e5;word-break:break-all;">%s</a>
                    </p>
                    """.formatted(buttonUrl, buttonText, buttonUrl, buttonUrl);
        }
        return """
                <!DOCTYPE html>
                <html><body style="margin:0;padding:0;background:#f3f4f6;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f4f6;padding:32px 16px;">
                  <tr><td align="center">
                    <table role="presentation" width="560" cellpadding="0" cellspacing="0"
                           style="background:#ffffff;border-radius:12px;overflow:hidden;max-width:560px;width:100%%;">
                      <tr><td style="background:#111827;padding:20px 32px;">
                        <span style="color:#ffffff;font-size:18px;font-weight:700;">Resume<span style="color:#818cf8;">AI</span></span>
                        <span style="color:#9ca3af;font-size:12px;margin-left:8px;">AI-Powered Resume Screening</span>
                      </td></tr>
                      <tr><td style="padding:32px;">
                        <h1 style="margin:0 0 16px;font-size:20px;color:#111827;">%s</h1>
                        <div style="font-size:14px;line-height:1.7;color:#374151;">%s</div>
                        %s
                      </td></tr>
                      <tr><td style="padding:20px 32px;background:#f9fafb;border-top:1px solid #e5e7eb;">
                        <p style="margin:0;font-size:12px;color:#9ca3af;">
                          This is an automated message from ResumeAI. Please do not reply to this email.
                        </p>
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
                </body></html>
                """.formatted(title, bodyHtml, button);
    }
}
