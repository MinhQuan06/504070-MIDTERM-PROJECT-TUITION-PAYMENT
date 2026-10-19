package vn.edu.tdtu.ibanking.paymentservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    @Bean
    public TopicExchange notificationExchange(@Value("${app.rabbitmq.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }
    @Bean
    public Queue notificationQueue(@Value("${app.rabbitmq.queue}") String name) {
        return QueueBuilder.durable(name).build();
    }
    @Bean
    public Binding otpBinding(Queue notificationQueue, TopicExchange notificationExchange,
                              @Value("${app.rabbitmq.otp-routing-key}") String key) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(key);
    }
    @Bean
    public Binding paymentSuccessBinding(Queue notificationQueue, TopicExchange notificationExchange,
                                         @Value("${app.rabbitmq.payment-success-routing-key}") String key) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(key);
    }
    @Bean
    public MessageConverter jsonMessageConverter() { return new JacksonJsonMessageConverter(); }
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setMandatory(true);
        // CorrelationData retains returns; do not log returned messages containing OTPs.
        template.setReturnsCallback(returned -> { });
        return template;
    }
}
