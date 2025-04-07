package com.saga.orchestrator.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaConfiguration {

  private final static Integer REPLICAS = 3;

  private final static Integer PARTITIONS = 3;

  @Value("${kafka.topic.name.product.actions}")
  private String productActionsTopic;

  @Value("${kafka.topic.name.product.events}")
  private String productEventsTopic;

  @Value("${kafka.topic.name.order.actions}")
  private String orderActionsTopic;

  @Value("${kafka.topic.name.order.events}")
  private String orderEventsTopic;

  @Value("${kafka.topic.name.payment.actions}")
  private String paymentActionsTopic;

  @Value("${kafka.topic.name.payment.events}")
  private String paymentEventsTopic;

  @Bean
  public KafkaTemplate<String, Object> kafkaTemplate(
      ProducerFactory<String, Object> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  @Bean
  public NewTopic productActionsTopic() {
    return TopicBuilder.name(this.productActionsTopic)
        .partitions(PARTITIONS)
        .replicas(REPLICAS)
        .build();
  }

  @Bean
  public NewTopic productEventsTopic() {
    return TopicBuilder.name(this.productEventsTopic)
        .partitions(PARTITIONS)
        .replicas(REPLICAS)
        .build();
  }

  @Bean
  public NewTopic orderActionsTopic() {
    return TopicBuilder.name(this.orderActionsTopic)
        .partitions(PARTITIONS)
        .replicas(REPLICAS)
        .build();
  }

  @Bean
  public NewTopic orderEventsTopic() {
    return TopicBuilder.name(this.orderEventsTopic)
        .partitions(PARTITIONS)
        .replicas(REPLICAS)
        .build();
  }

  @Bean
  public NewTopic paymentActionsTopic() {
    return TopicBuilder.name(this.paymentActionsTopic)
        .partitions(PARTITIONS)
        .replicas(REPLICAS)
        .build();
  }

  @Bean
  public NewTopic paymentEventsTopic() {
    return TopicBuilder.name(this.paymentEventsTopic)
        .partitions(PARTITIONS)
        .replicas(REPLICAS)
        .build();
  }
}
