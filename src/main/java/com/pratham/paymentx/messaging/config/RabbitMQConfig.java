package com.pratham.paymentx.messaging.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
public class RabbitMQConfig {

    // Exchange Names
    public static final String EVENTS_EXCHANGE = "paymentx.events";
    public static final String DLX_EXCHANGE = "paymentx.dlx";

    // Routing Keys
    public static final String MERCHANT_APPROVAL_ROUTING_KEY = "merchant.approval";
    public static final String MERCHANT_APPROVAL_DLQ_ROUTING_KEY = "merchant.approval.dead";
    public static final String TOPUP_FULFILLMENT_ROUTING_KEY = "topup.fulfillment";
    public static final String TOPUP_FULFILLMENT_DLQ_ROUTING_KEY = "topup.fulfillment.dead";
    public static final String PAYOUT_RECON_ROUTING_KEY = "payout.reconciliation";
    public static final String PAYOUT_RECON_DLQ_ROUTING_KEY = "payout.reconciliation.dead";
    public static final String EMAIL_ROUTING_KEY = "email.notification";
    public static final String EMAIL_DLQ_ROUTING_KEY = "email.notification.dead";


    // Queue Names
    public static final String MERCHANT_APPROVAL_QUEUE = "merchant.approval";
    public static final String MERCHANT_APPROVAL_DLQ = "merchant.approval.dead";
    public static final String TOPUP_FULFILLMENT_QUEUE = "topup.fulfillment";
    public static final String TOPUP_FULFILLMENT_DLQ = "topup.fulfillment.dead";
    public static final String PAYOUT_RECON_QUEUE = "payout.reconciliation";
    public static final String PAYOUT_RECON_DLQ = "payout.reconciliation.dead";
    public static final String EMAIL_QUEUE = "email.notification";
    public static final String EMAIL_DLQ = "email.notification.dead";


    // Retry Config
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_INTERVAL_MS = 2_000;   // 2s
    private static final double BACKOFF_MULTIPLIER = 2.0;     // 2s → 4s → 8s
    private static final long MAX_INTERVAL_MS = 10_000;       // cap at 10s

    // Exchanges
    @Bean
    public TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(EVENTS_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange dlxExchange() {
        return ExchangeBuilder.directExchange(DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    // Queues
    @Bean
    public Queue merchantApprovalQueue() {
        return QueueBuilder.durable(MERCHANT_APPROVAL_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MERCHANT_APPROVAL_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue merchantApprovalDlq() {
        return QueueBuilder.durable(MERCHANT_APPROVAL_DLQ).build();
    }

    @Bean
    public Queue topupFulfillmentQueue() {
        return QueueBuilder.durable(TOPUP_FULFILLMENT_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", TOPUP_FULFILLMENT_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue topupFulfillmentDlq() {
        return QueueBuilder.durable(TOPUP_FULFILLMENT_DLQ).build();
    }

    @Bean
    public Queue payoutReconQueue() {
        return QueueBuilder.durable(PAYOUT_RECON_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PAYOUT_RECON_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue payoutReconDlq() {
        return QueueBuilder.durable(PAYOUT_RECON_DLQ).build();
    }

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", EMAIL_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue emailDlq() {
        return QueueBuilder.durable(EMAIL_DLQ).build();
    }

    // Bindings
    @Bean
    public Binding merchantApprovalBinding() {
        return BindingBuilder
                .bind(merchantApprovalQueue())
                .to(eventsExchange())
                .with(MERCHANT_APPROVAL_ROUTING_KEY);
    }

    @Bean
    public Binding merchantApprovalDlqBinding() {
        return BindingBuilder
                .bind(merchantApprovalDlq())
                .to(dlxExchange())
                .with(MERCHANT_APPROVAL_DLQ_ROUTING_KEY);
    }

    @Bean
    public Binding topupFulfillmentBinding() {
        return BindingBuilder
                .bind(topupFulfillmentQueue())
                .to(eventsExchange())
                .with(TOPUP_FULFILLMENT_ROUTING_KEY);
    }

    @Bean
    public Binding topupFulfillmentDlqBinding() {
        return BindingBuilder
                .bind(topupFulfillmentDlq())
                .to(dlxExchange())
                .with(TOPUP_FULFILLMENT_DLQ_ROUTING_KEY);
    }

    @Bean
    public Binding payoutReconBinding() {
        return BindingBuilder.bind(payoutReconQueue()).to(eventsExchange()).with(PAYOUT_RECON_ROUTING_KEY);
    }

    @Bean
    public Binding payoutReconDlqBinding() {
        return BindingBuilder.bind(payoutReconDlq()).to(dlxExchange()).with(PAYOUT_RECON_DLQ_ROUTING_KEY);
    }

    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue()).to(eventsExchange()).with(EMAIL_ROUTING_KEY);
    }

    @Bean
    public Binding emailDlqBinding() {
        return BindingBuilder.bind(emailDlq()).to(dlxExchange()).with(EMAIL_DLQ_ROUTING_KEY);
    }

    // Message Converter
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // Retry Interceptor
    public RetryOperationsInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(MAX_ATTEMPTS)
                .backOffOptions(INITIAL_INTERVAL_MS, BACKOFF_MULTIPLIER, MAX_INTERVAL_MS)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAdviceChain(retryInterceptor());
        return factory;
    }
}