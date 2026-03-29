package com.ying.tech.community.web.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑配置
 *
 * <p>策略链路：发送消息 → Confirm/Return(旁路日志) → 消息/队列持久化 → 幂等拦截 → 手动ACK → 重试/DLX
 *
 * <p>交换机（durable）：
 * <ul>
 *   <li>article.fanout  —— 文章发布广播（FanoutExchange）</li>
 *   <li>article.direct  —— 定向消息，如时间轴重建（DirectExchange）</li>
 *   <li>article.dlx     —— 死信交换机（DirectExchange）</li>
 * </ul>
 *
 * <p>主队列（durable，携带 x-dead-letter-exchange 参数）：
 * <ul>
 *   <li>article.publish.queue  → 消费文章发布消息</li>
 *   <li>timeline.rebuild.queue → 消费时间轴重建消息</li>
 * </ul>
 *
 * <p>死信队列（durable，绑定到 article.dlx，用于人工补偿）：
 * <ul>
 *   <li>article.publish.dlq</li>
 *   <li>timeline.rebuild.dlq</li>
 * </ul>
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    // ===================== 交换机 =====================
    public static final String ARTICLE_FANOUT_EXCHANGE  = "article.fanout";
    public static final String ARTICLE_DIRECT_EXCHANGE  = "article.direct";
    public static final String ARTICLE_DLX_EXCHANGE     = "article.dlx";

    // ===================== 主队列 =====================
    public static final String ARTICLE_PUBLISH_QUEUE    = "article.publish.queue";
    public static final String TIMELINE_REBUILD_QUEUE   = "timeline.rebuild.queue";
    public static final String ARTICLE_LIKE_QUEUE  = "article.like.queue";

    // ===================== 死信队列 =====================
    public static final String ARTICLE_PUBLISH_DLQ      = "article.publish.dlq";
    public static final String TIMELINE_REBUILD_DLQ     = "timeline.rebuild.dlq";
    public static final String ARTICLE_LIKE_DLQ      = "article.like.dlq";

    // ===================== 路由键 =====================
    public static final String TIMELINE_REBUILD_KEY     = "timeline.rebuild";
    private static final String ARTICLE_PUBLISH_DEAD_KEY  = "article.publish.dead";
    private static final String TIMELINE_REBUILD_DEAD_KEY = "timeline.rebuild.dead";
    private static final String ARTICLE_LIKE_DEAD_KEY  = "article.like.dead";
    private static final String ARTICLE_LIKE_KEY  = "article.like";

    // -----------------------------------------------------------------------
    // 消息转换器：JSON 序列化，生产者/消费者两侧统一
    // Spring Boot 会自动将此 Bean 注入 RabbitTemplate 和 ListenerContainerFactory
    // -----------------------------------------------------------------------
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // -----------------------------------------------------------------------
    // 旁路日志回调：ConfirmCallback + ReturnsCallback
    // 不阻塞主流程，仅记录日志，方便排查消息丢失问题
    // -----------------------------------------------------------------------
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);

        // ConfirmCallback：消息是否成功到达 Broker（Exchange 级别应答）
        template.setConfirmCallback((correlationData, ack, cause) -> {
            String messageId = (correlationData != null) ? correlationData.getId() : "N/A";
            if (ack) {
                log.info("[MQ Confirm] 消息已投递至 Broker, messageId: {}", messageId);
            } else {
                log.error("[MQ Confirm] 消息投递 Broker 失败, messageId: {}, cause: {}", messageId, cause);
            }
        });

        // ReturnsCallback：消息到达 Exchange 但无法路由到任何队列时触发（mandatory=true 生效）
        template.setReturnsCallback(returned -> log.error(
                "[MQ Return] 消息路由失败, exchange: {}, routingKey: {}, replyCode: {}, replyText: {}, body: {}",
                returned.getExchange(), returned.getRoutingKey(),
                returned.getReplyCode(), returned.getReplyText(),
                new String(returned.getMessage().getBody())));

        return template;
    }

    // -----------------------------------------------------------------------
    // 交换机声明（durable=true）
    // -----------------------------------------------------------------------
    @Bean
    public FanoutExchange articleFanoutExchange() {
        return ExchangeBuilder.fanoutExchange(ARTICLE_FANOUT_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange articleDirectExchange() {
        return ExchangeBuilder.directExchange(ARTICLE_DIRECT_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange articleDlxExchange() {
        return ExchangeBuilder.directExchange(ARTICLE_DLX_EXCHANGE).durable(true).build();
    }

    // -----------------------------------------------------------------------
    // 主队列声明（durable=true + x-dead-letter-exchange 参数）
    // 消费者调用 basicNack(requeue=false) 后，消息自动路由至 DLX
    // -----------------------------------------------------------------------
    @Bean
    public Queue articlePublishQueue() {
        return QueueBuilder.durable(ARTICLE_PUBLISH_QUEUE)
                .withArgument("x-dead-letter-exchange", ARTICLE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ARTICLE_PUBLISH_DEAD_KEY)
                .build();
    }

    @Bean
    public Queue timelineRebuildQueue() {
        return QueueBuilder.durable(TIMELINE_REBUILD_QUEUE)
                .withArgument("x-dead-letter-exchange", ARTICLE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", TIMELINE_REBUILD_DEAD_KEY)
                .build();
    }

    @Bean
    public Queue articleLikeQueue() {
        return QueueBuilder.durable(ARTICLE_LIKE_QUEUE)
                .withArgument("x-dead-letter-exchange", ARTICLE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ARTICLE_LIKE_DEAD_KEY)
                .build();
    }

    // -----------------------------------------------------------------------
    // 死信队列声明（durable=true）
    // -----------------------------------------------------------------------
    @Bean
    public Queue articlePublishDlq() {
        return QueueBuilder.durable(ARTICLE_PUBLISH_DLQ).build();
    }

    @Bean
    public Queue timelineRebuildDlq() {
        return QueueBuilder.durable(TIMELINE_REBUILD_DLQ).build();
    }

    @Bean
    public Queue articleLikeDlq() {
        return QueueBuilder.durable(ARTICLE_LIKE_DLQ).build();
    }

    // -----------------------------------------------------------------------
    // 绑定：主队列 → 交换机
    // -----------------------------------------------------------------------
    @Bean
    public Binding articlePublishBinding(Queue articlePublishQueue,
                                          FanoutExchange articleFanoutExchange) {
        return BindingBuilder.bind(articlePublishQueue).to(articleFanoutExchange);
    }

    @Bean
    public Binding timelineRebuildBinding(Queue timelineRebuildQueue,
                                           DirectExchange articleDirectExchange) {
        return BindingBuilder.bind(timelineRebuildQueue)
                .to(articleDirectExchange)
                .with(TIMELINE_REBUILD_KEY);
    }

    @Bean
    public Binding articleLikeBinding(Queue articleLikeQueue,
                                           DirectExchange articleDirectExchange) {
        return BindingBuilder.bind(articleLikeQueue)
                .to(articleDirectExchange)
                .with(ARTICLE_LIKE_KEY);
    }

    // -----------------------------------------------------------------------
    // 绑定：死信队列 → DLX
    // -----------------------------------------------------------------------
    @Bean
    public Binding articlePublishDlqBinding(Queue articlePublishDlq,
                                              DirectExchange articleDlxExchange) {
        return BindingBuilder.bind(articlePublishDlq)
                .to(articleDlxExchange)
                .with(ARTICLE_PUBLISH_DEAD_KEY);
    }

    @Bean
    public Binding timelineRebuildDlqBinding(Queue timelineRebuildDlq,
                                               DirectExchange articleDlxExchange) {
        return BindingBuilder.bind(timelineRebuildDlq)
                .to(articleDlxExchange)
                .with(TIMELINE_REBUILD_DEAD_KEY);
    }

    @Bean
    public Binding articleLikeDlqBinding(Queue articleLikeDlq,
                                               DirectExchange articleDlxExchange) {
        return BindingBuilder.bind(articleLikeDlq)
                .to(articleDlxExchange)
                .with(ARTICLE_LIKE_DEAD_KEY);
    }

    // -----------------------------------------------------------------------
    // 监听器容器工厂
    // configurer.configure() 会读取 yml 的 listener.simple.* 配置：
    //   ack 模式、prefetch、retry 次数/退避参数 等均在 application.yml 中维护。
    // 此处只额外注入 RejectAndDontRequeueRecoverer：
    //   重试耗尽后抛出 AmqpRejectAndDontRequeueException
    //   → Spring AMQP 自动 basicNack(requeue=false) → 消息路由至 DLX
    // -----------------------------------------------------------------------
    @Bean
    public SimpleRabbitListenerContainerFactory manualAckListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            RabbitProperties properties) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        // 应用 yml 中的 ack 模式、prefetch、消息转换器、retry 次数/退避等全部基础配置
        configurer.configure(factory, connectionFactory);

        // 在 yml retry 参数基础上，注入 RejectAndDontRequeueRecoverer 保证耗尽后路由至 DLX
        RabbitProperties.ListenerRetry retry = properties.getListener().getSimple().getRetry();
        if (retry.isEnabled()) {
            factory.setAdviceChain(
                    RetryInterceptorBuilder.stateless()
                            .maxAttempts(retry.getMaxAttempts())
                            .backOffOptions(
                                    retry.getInitialInterval().toMillis(),
                                    retry.getMultiplier(),
                                    retry.getMaxInterval().toMillis())
                            .recoverer(new RejectAndDontRequeueRecoverer())
                            .build()
            );
        }
        return factory;
    }

    // -----------------------------------------------------------------------
    // 监听器容器工厂（Auto ACK + 失败丢弃）
    // 用于非关键业务：发送 → Queue → Auto ACK → 失败丢弃
    // 适用于：timeline.rebuild.queue 等允许偶尔失败的场景
    // -----------------------------------------------------------------------
    @Bean
    public SimpleRabbitListenerContainerFactory autoAckListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);  // Auto ACK
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(3);
        factory.setPrefetchCount(10);
        // 不配置重试，失败直接丢弃
        return factory;
    }
}