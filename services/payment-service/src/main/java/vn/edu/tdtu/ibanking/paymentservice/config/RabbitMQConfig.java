package vn.edu.tdtu.ibanking.paymentservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
// Đổi import từ Jackson2JsonMessageConverter sang JacksonJsonMessageConverter
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.otp-queue}")
    private String otpQueueName;

    @Value("${app.rabbitmq.otp-routing-key}")
    private String otpRoutingKey;

    @Value("${app.rabbitmq.payment-success-queue}")
    private String paymentSuccessQueueName;

    @Value("${app.rabbitmq.payment-success-routing-key}")
    private String paymentSuccessRoutingKey;

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue otpQueue() {
        return QueueBuilder.durable(otpQueueName).build();
    }

    @Bean
    public Queue paymentSuccessQueue() {
        return QueueBuilder.durable(paymentSuccessQueueName).build();
    }

    @Bean
    public Binding otpBinding(Queue otpQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(otpQueue).to(notificationExchange).with(otpRoutingKey);
    }

    @Bean
    public Binding paymentSuccessBinding(Queue paymentSuccessQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(paymentSuccessQueue).to(notificationExchange).with(paymentSuccessRoutingKey);
    }

    // Sử dụng JacksonJsonMessageConverter (dành cho Jackson 3 trên Spring Boot 4)
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}