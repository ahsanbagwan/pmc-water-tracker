package org.punewatertracker.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class ReportNotificationListener {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.admin-notification-email:}")
    private String adminNotificationEmail;

    public ReportNotificationListener(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @RabbitListener(queues = RabbitMQConfig.REPORT_SUBMITTED_QUEUE)
    public void onReportSubmitted(ReportSubmittedEvent event) {
        if (mailUsername.isBlank() || adminNotificationEmail.isBlank()) {
            System.out.println("[report-notification] Skipping email (MAIL_USERNAME or "
                    + "ADMIN_NOTIFICATION_EMAIL not set). Report on '" + event.localityName()
                    + "' was still saved -- this only affects the notification.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailUsername);
            message.setTo(adminNotificationEmail);
            message.setSubject("New water status report: " + event.localityName());
            message.setText(
                    "A citizen submitted a report for " + event.localityName() + ".\n\n"
                            + "Proposed status: " + event.proposedStatus() + "\n"
                            + "Notes: " + (event.notes() == null || event.notes().isBlank() ? "(none)" : event.notes()) + "\n"
                            + "Submitted at: " + event.submittedAt() + "\n"
                            + "Locality id: " + event.localityId() + "\n\n"
                            + "Review and approve it with:\n"
                            + "POST /api/localities/reports/" + event.localityId() + "/approve"
            );
            mailSender.send(message);
        } catch (Exception ex) {
            System.out.println("[report-notification] Failed to send email for '" + event.localityName()
                    + "': " + ex.getMessage() + ". The report itself was still saved successfully.");
        }
    }
}
