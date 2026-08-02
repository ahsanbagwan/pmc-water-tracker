package org.punewatertracker.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class ReportNotificationListener {
    @RabbitListener(queues = RabbitMQConfig.REPORT_SUBMITTED_QUEUE)
    public void onReportSubmitted(ReportSubmittedEvent event) {
        System.out.println("[report-notification] New citizen report on '" + event.localityName()
                + "' (locality id " + event.localityId() + ", proposed status " + event.proposedStatus()
                + ") submitted at " + event.submittedAt() + ". Notes: " + event.notes());
    }
}
