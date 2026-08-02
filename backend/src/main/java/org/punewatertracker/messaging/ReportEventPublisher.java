package org.punewatertracker.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReportEventPublisher {
    @Value("${app.rabbitmq.enabled:false}")
    private boolean enabled;

    private final RabbitTemplate rabbitTemplate;

    public ReportEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Fire-and-forget: report submission has already succeeded (saved to the DB) by the time
     * this is called, so a disabled/unreachable broker never blocks or fails the citizen's
     * request -- it just means no async notification goes out.
     */
    public void publishReportSubmitted(ReportSubmittedEvent event) {
        if (!enabled) {
            return;
        }
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.REPORT_SUBMITTED_ROUTING_KEY, event);
    }
}
