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
 *   <li>timeline.rebuild.queue → 消费时间轴重建消息</li>
 * </ul>
 *
 * <p>死信队列（durable，绑定到 article.dlx，用于人工补偿）：
 * <ul>
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

    public static final String NOTIFY_DIRECT_EXCHANGE     = "notify.direct";


    // ===================== 主队列 =====================
    public static final String ARTICLE_PUBLISH_REVIEW_QUEUE  = "article.publish.review.queue";
    public static final String ARTICLE_PUBLISH_TIMELINE_QUEUE = "article.publish.timeline.queue";
    public static final String ARTICLE_PUBLISH_NOTIFY_QUEUE = "article.publish.notify.queue";
    public static final String ARTICLE_PUBLISH_ES_QUEUE = "article.publish.es.queue";
    public static final String TIMELINE_REBUILD_QUEUE   = "timeline.rebuild.queue";
    public static final String ARTICLE_LIKE_QUEUE  = "article.like.queue";
    // 收藏及通知相关主队列。
    public static final String ARTICLE_COLLECT_QUEUE = "article.collect.queue";
    public static final String ARTICLE_LIKE_NOTIFY_QUEUE = "article.like.notify.queue";
    public static final String ARTICLE_COLLECT_NOTIFY_QUEUE = "article.collect.notify.queue";
    public static final String USER_FOLLOW_NOTIFY_QUEUE = "user.follow.notify.queue";

    public static final String NOTIFY_PUBLISH_FAIL_QUEUE = "notify.publish.fail.queue";

    // AI 向量同步主队列。
    public static final String AI_EMBEDDING_QUEUE = "ai.embedding.queue";

    // ===================== 死信队列 =====================
    public static final String ARTICLE_PUBLISH_REVIEW_DLQ  = "article.publish.review.dlq";
    public static final String ARTICLE_PUBLISH_TIMELINE_DLQ = "article.publish.timeline.dlq";
    public static final String ARTICLE_PUBLISH_NOTIFY_DLQ = "article.publish.notify.dlq";
    public static final String ARTICLE_PUBLISH_ES_DLQ = "article.publish.es.dlq";
    public static final String TIMELINE_REBUILD_DLQ     = "timeline.rebuild.dlq";
    public static final String ARTICLE_LIKE_DLQ      = "article.like.dlq";
    // 收藏及通知相关死信队列。
    public static final String ARTICLE_COLLECT_DLQ = "article.collect.dlq";
    public static final String ARTICLE_LIKE_NOTIFY_DLQ = "article.like.notify.dlq";
    public static final String ARTICLE_COLLECT_NOTIFY_DLQ = "article.collect.notify.dlq";
    public static final String USER_FOLLOW_NOTIFY_DLQ = "user.follow.notify.dlq";
    // AI 向量同步死信队列。
    public static final String AI_EMBEDDING_DLQ = "ai.embedding.dlq";

    // ===================== 路由键 =====================
    private static final String ARTICLE_PUBLISH_REVIEW_KEY = "article.publish.review";
    private static final String TIMELINE_REBUILD_KEY = "timeline.rebuild";
    private static final String ARTICLE_LIKE_KEY  = "article.like";
    // 收藏及通知相关路由键。
    private static final String ARTICLE_COLLECT_KEY = "article.collect";
    private static final String ARTICLE_LIKE_NOTIFY_KEY = "article.like.notify";
    private static final String ARTICLE_COLLECT_NOTIFY_KEY = "article.collect.notify";
    private static final String USER_FOLLOW_NOTIFY_KEY = "user.follow.notify";
    private static final String ARTICLE_PUBLISH_REVIEW_DEAD_KEY = "article.publish.review.dead";
    private static final String ARTICLE_PUBLISH_TIMELINE_DEAD_KEY = "article.publish.timeline.dead";
    private static final String ARTICLE_PUBLISH_NOTIFY_DEAD_KEY = "article.publish.notify.dead";
    private static final String ARTICLE_PUBLISH_ES_DEAD_KEY = "article.publish.es.dead";
    private static final String TIMELINE_REBUILD_DEAD_KEY = "timeline.rebuild.dead";
    private static final String ARTICLE_LIKE_DEAD_KEY  = "article.like.dead";
    private static final String ARTICLE_COLLECT_DEAD_KEY = "article.collect.dead";
    private static final String ARTICLE_LIKE_NOTIFY_DEAD_KEY = "article.like.notify.dead";
    private static final String ARTICLE_COLLECT_NOTIFY_DEAD_KEY = "article.collect.notify.dead";
    private static final String USER_FOLLOW_NOTIFY_DEAD_KEY = "user.follow.notify.dead";

    private static final String NOTIFY_PUBLISH_FAIL_KEY = "notify.publish.fail";

    // AI 向量同步路由键。
    private static final String AI_EMBEDDING_KEY = "ai.embedding";
    private static final String AI_EMBEDDING_DEAD_KEY = "ai.embedding.dead";


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

    @Bean
    public DirectExchange notifyDirectExchange() {
        return ExchangeBuilder.directExchange(NOTIFY_DIRECT_EXCHANGE).durable(true).build();
    }

    // -----------------------------------------------------------------------
    // 主队列声明（durable=true + x-dead-letter-exchange 参数）
    // 消费者调用 basicNack(requeue=false) 后，消息自动路由至 DLX
    // -----------------------------------------------------------------------
    @Bean
    public Queue articlePublishReviewQueue() {
        return QueueBuilder.durable(ARTICLE_PUBLISH_REVIEW_QUEUE)
                .withArgument("x-dead-letter-exchange", ARTICLE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ARTICLE_PUBLISH_REVIEW_DEAD_KEY)
                .build();
    }
    @Bean
    public Queue articlePublishTimelineQueue() {
        return QueueBuilder.durable(ARTICLE_PUBLISH_TIMELINE_QUEUE)
                .withArgument("x-dead-letter-exchange", ARTICLE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ARTICLE_PUBLISH_TIMELINE_DEAD_KEY)
                .build();
    }

    @Bean
    public Queue articlePublishNotifyQueue() {
        return QueueBuilder.durable(ARTICLE_PUBLISH_NOTIFY_QUEUE)
                .withArgument("x-dead-letter-exchange", ARTICLE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ARTICLE_PUBLISH_NOTIFY_DEAD_KEY)
                .build();
    }

    @Bean
    public Queue articlePublishEsQueue() {
        return QueueBuilder.durable(ARTICLE_PUBLISH_ES_QUEUE)
                .withArgument("x-dead-letter-exchange", ARTICLE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ARTICLE_PUBLISH_ES_DEAD_KEY)
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

    @Bean
    public Queue articleCollectQueue() {
        return QueueBuilder.durable(ARTICLE_COLLECT_QUEUE)
                .withArgument("x-dead-letter-exchange", ARTICLE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ARTICLE_COLLECT_DEAD_KEY)
                .build();
    }

    @Bean
    public Queue articleLikeNotifyQueue() {
        return QueueBuilder.durable(ARTICLE_LIKE_NOTIFY_QUEUE)
                .withArgument("x-dead-letter-exchange", ARTICLE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ARTICLE_LIKE_NOTIFY_DEAD_KEY)
                .build();
    }

    @Bean
    public Queue articleCollectNotifyQueue() {
        return QueueBuilder.durable(ARTICLE_COLLECT_NOTIFY_QUEUE)
                .withArgument("x-dead-letter-exchange", ARTICLE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ARTICLE_COLLECT_NOTIFY_DEAD_KEY)
                .build();
    }

    @Bean
    public Queue userFollowNotifyQueue() {
        return QueueBuilder.durable(USER_FOLLOW_NOTIFY_QUEUE)
                .withArgument("x-dead-letter-exchange", ARTICLE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", USER_FOLLOW_NOTIFY_DEAD_KEY)
                .build();
    }

    @Bean
    public Queue notifyPublishFailQueue(){
        return QueueBuilder.durable(NOTIFY_PUBLISH_FAIL_QUEUE)
                .build();
    }

    @Bean
    public Queue aiEmbeddingQueue(){
        return QueueBuilder.durable(AI_EMBEDDING_QUEUE)
                .withArgument("x-dead-letter-exchange", ARTICLE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", AI_EMBEDDING_DEAD_KEY)
                .build();
    }
    // -----------------------------------------------------------------------
    // 死信队列声明（durable=true）
    // -----------------------------------------------------------------------
    @Bean
    public Queue articlePublishReviewDlq() {
        return QueueBuilder.durable(ARTICLE_PUBLISH_REVIEW_DLQ).build();
    }
    @Bean
    public Queue articlePublishTimelineDlq() {
        return QueueBuilder.durable(ARTICLE_PUBLISH_TIMELINE_DLQ).build();
    }

    @Bean
    public Queue articlePublishNotifyDlq() {
        return QueueBuilder.durable(ARTICLE_PUBLISH_NOTIFY_DLQ).build();
    }

    @Bean
    public Queue articlePublishEsDlq() {
        return QueueBuilder.durable(ARTICLE_PUBLISH_ES_DLQ).build();
    }

    @Bean
    public Queue timelineRebuildDlq() {
        return QueueBuilder.durable(TIMELINE_REBUILD_DLQ).build();
    }

    @Bean
    public Queue articleLikeDlq() {
        return QueueBuilder.durable(ARTICLE_LIKE_DLQ).build();
    }

    @Bean
    public Queue articleCollectDlq() {
        return QueueBuilder.durable(ARTICLE_COLLECT_DLQ).build();
    }

    @Bean
    public Queue articleLikeNotifyDlq() {
        return QueueBuilder.durable(ARTICLE_LIKE_NOTIFY_DLQ).build();
    }

    @Bean
    public Queue articleCollectNotifyDlq() {
        return QueueBuilder.durable(ARTICLE_COLLECT_NOTIFY_DLQ).build();
    }

    @Bean
    public Queue userFollowNotifyDlq() {
        return QueueBuilder.durable(USER_FOLLOW_NOTIFY_DLQ).build();
    }

    @Bean
    public Queue aiEmbeddingDlq(){
        return QueueBuilder.durable(AI_EMBEDDING_DLQ).build();
    }
    // -----------------------------------------------------------------------
    // 绑定：主队列 → 交换机
    // -----------------------------------------------------------------------
    @Bean
    public Binding articlePublishReviewBinding(Queue articlePublishReviewQueue,
                                               DirectExchange articleDirectExchange) {
        return BindingBuilder.bind(articlePublishReviewQueue)
                .to(articleDirectExchange)
                .with(ARTICLE_PUBLISH_REVIEW_KEY);
    }
    @Bean
    public Binding articlePublishTimelineBinding(Queue articlePublishTimelineQueue,
                                                 FanoutExchange articleFanoutExchange) {
        return BindingBuilder.bind(articlePublishTimelineQueue).to(articleFanoutExchange);
    }

    @Bean
    public Binding articlePublishNotifyBinding(Queue articlePublishNotifyQueue,
                                               FanoutExchange articleFanoutExchange) {
        return BindingBuilder.bind(articlePublishNotifyQueue).to(articleFanoutExchange);
    }

    @Bean
    public Binding articlePublishEsBinding(Queue articlePublishEsQueue,
                                           FanoutExchange articleFanoutExchange) {
        return BindingBuilder.bind(articlePublishEsQueue).to(articleFanoutExchange);
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

    @Bean
    public Binding articleCollectBinding(Queue articleCollectQueue,
                                         DirectExchange articleDirectExchange) {
        return BindingBuilder.bind(articleCollectQueue)
                .to(articleDirectExchange)
                .with(ARTICLE_COLLECT_KEY);
    }

    @Bean
    public Binding articleLikeNotifyBinding(Queue articleLikeNotifyQueue,
                                            DirectExchange notifyDirectExchange) {
        return BindingBuilder.bind(articleLikeNotifyQueue)
                .to(notifyDirectExchange)
                .with(ARTICLE_LIKE_NOTIFY_KEY);
    }

    @Bean
    public Binding articleCollectNotifyBinding(Queue articleCollectNotifyQueue,
                                               DirectExchange notifyDirectExchange) {
        return BindingBuilder.bind(articleCollectNotifyQueue)
                .to(notifyDirectExchange)
                .with(ARTICLE_COLLECT_NOTIFY_KEY);
    }

    @Bean
    public Binding userFollowNotifyBinding(Queue userFollowNotifyQueue,
                                           DirectExchange notifyDirectExchange) {
        return BindingBuilder.bind(userFollowNotifyQueue)
                .to(notifyDirectExchange)
                .with(USER_FOLLOW_NOTIFY_KEY);
    }

    @Bean
    public Binding notifyPublishFailBinding(Queue notifyPublishFailQueue,
                                           DirectExchange notifyDirectExchange) {
        return BindingBuilder.bind(notifyPublishFailQueue)
                .to(notifyDirectExchange)
                .with(NOTIFY_PUBLISH_FAIL_KEY);
    }

    @Bean
    public Binding aiEmbeddingBinding(Queue aiEmbeddingQueue,
                                      FanoutExchange articleFanoutExchange) {
        return BindingBuilder.bind(aiEmbeddingQueue).to(articleFanoutExchange);
    }

    // -----------------------------------------------------------------------
    // 绑定：死信队列 → DLX
    // -----------------------------------------------------------------------
    @Bean
    public Binding articlePublishReviewDlqBinding(Queue articlePublishReviewDlq,
                                                  DirectExchange articleDlxExchange) {
        return BindingBuilder.bind(articlePublishReviewDlq)
                .to(articleDlxExchange)
                .with(ARTICLE_PUBLISH_REVIEW_DEAD_KEY);
    }
    @Bean
    public Binding articlePublishTimelineDlqBinding(Queue articlePublishTimelineDlq,
                                                    DirectExchange articleDlxExchange) {
        return BindingBuilder.bind(articlePublishTimelineDlq)
                .to(articleDlxExchange)
                .with(ARTICLE_PUBLISH_TIMELINE_DEAD_KEY);
    }

    @Bean
    public Binding articlePublishNotifyDlqBinding(Queue articlePublishNotifyDlq,
                                                  DirectExchange articleDlxExchange) {
        return BindingBuilder.bind(articlePublishNotifyDlq)
                .to(articleDlxExchange)
                .with(ARTICLE_PUBLISH_NOTIFY_DEAD_KEY);
    }

    @Bean
    public Binding articlePublishEsDlqBinding(Queue articlePublishEsDlq,
                                              DirectExchange articleDlxExchange) {
        return BindingBuilder.bind(articlePublishEsDlq)
                .to(articleDlxExchange)
                .with(ARTICLE_PUBLISH_ES_DEAD_KEY);
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

    @Bean
    public Binding articleCollectDlqBinding(Queue articleCollectDlq,
                                            DirectExchange articleDlxExchange) {
        return BindingBuilder.bind(articleCollectDlq)
                .to(articleDlxExchange)
                .with(ARTICLE_COLLECT_DEAD_KEY);
    }

    @Bean
    public Binding articleLikeNotifyDlqBinding(Queue articleLikeNotifyDlq,
                                               DirectExchange articleDlxExchange) {
        return BindingBuilder.bind(articleLikeNotifyDlq)
                .to(articleDlxExchange)
                .with(ARTICLE_LIKE_NOTIFY_DEAD_KEY);
    }

    @Bean
    public Binding articleCollectNotifyDlqBinding(Queue articleCollectNotifyDlq,
                                                  DirectExchange articleDlxExchange) {
        return BindingBuilder.bind(articleCollectNotifyDlq)
                .to(articleDlxExchange)
                .with(ARTICLE_COLLECT_NOTIFY_DEAD_KEY);
    }

    @Bean
    public Binding userFollowNotifyDlqBinding(Queue userFollowNotifyDlq,
                                              DirectExchange articleDlxExchange) {
        return BindingBuilder.bind(userFollowNotifyDlq)
                .to(articleDlxExchange)
                .with(USER_FOLLOW_NOTIFY_DEAD_KEY);
    }

    @Bean
    public Binding aiEmbeddingDlqBinding(Queue aiEmbeddingDlq,
                                           DirectExchange articleDlxExchange) {
        return BindingBuilder.bind(aiEmbeddingDlq)
                .to(articleDlxExchange)
                .with(AI_EMBEDDING_DEAD_KEY);
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
