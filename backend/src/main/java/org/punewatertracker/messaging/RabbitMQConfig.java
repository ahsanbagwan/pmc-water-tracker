package org.punewatertracker.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class RabbitMQConfig {
    public static final String EXCHANGE = "pmc-water-tracker-exchange";
    public static final String REPORT_SUBMITTED_QUEUE = "report-submitted-queue";
    public static final String REPORT_SUBMITTED_ROUTING_KEY = "report.submitted";

    @Bean
    public TopicExchange reportExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue reportSubmittedQueue() {
        return new Queue(REPORT_SUBMITTED_QUEUE, true); // durable -- survives a broker restart
    }

    @Bean
    public Binding reportSubmittedBinding(Queue reportSubmittedQueue, TopicExchange reportExchange) {
        return BindingBuilder.bind(reportSubmittedQueue).to(reportExchange).with(REPORT_SUBMITTED_ROUTING_KEY);
    }

    /** JSON instead of Java serialization -- readable on the wire, no consumer classpath coupling. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
