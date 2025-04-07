package com.saga.product.kafka;

import com.saga.dto.product.ProductReservationFailedEvent;
import com.saga.dto.product.ProductReserveAction;
import com.saga.dto.product.ProductReservedEvent;
import com.saga.dto.product.ProductRollbackAction;
import com.saga.dto.product.ProductRollbackDoneEvent;
import com.saga.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@KafkaListener(topics = {"${kafka.topic.name.product.actions}"})
@Slf4j
public class ProductHandler {

  private static final Integer RESERVED_QUANTITY = 1;

  @Value("${kafka.topic.name.product.events}")
  private String productEventsTopic;

  private final ProductService productService;

  private final KafkaTemplate<String, Object> kafkaTemplate;

  @KafkaHandler
  public void handleProductAction(@Payload final ProductReserveAction action) {
    log.info("handleProductEvent. Received ProductReserveAction: {} ", action);
    try {

      this.productService.buyProduct(action.getProductId());
      final ProductReservedEvent event = new ProductReservedEvent(action.getProductId(),
          RESERVED_QUANTITY);

      this.kafkaTemplate.send(this.productEventsTopic, event);

      log.info("handleProductEvent. Sent ProductReservedEvent: {} ", event);

    } catch (Exception e) {

      log.error("handleProductEvent. Error while buying product: {}", e.getMessage());
      final ProductReservationFailedEvent event = new ProductReservationFailedEvent(
          action.getProductId(), e.getMessage());

      this.kafkaTemplate.send(this.productEventsTopic, event);

      log.info("handleProductEvent. Sent ProductReservationFailedEvent: {} ", event);

    }


  }

  @KafkaHandler
  public void handleProductAction(@Payload final ProductRollbackAction action) {
    log.info("handleProductEvent. Received ProductRollbackAction: {} ", action);
    try {
      this.productService.rollbackProduct(action.getProductId());
    } catch (Exception e) {
      //HERE WE SHOULD THROW A RETRYABLE EXCEPTION
    }

    final ProductRollbackDoneEvent event = new ProductRollbackDoneEvent(action.getProductId());

    this.kafkaTemplate.send(this.productEventsTopic, event);

    log.info("handleProductEvent. Sent ProductRollbackDoneEvent: {} ", event);
  }

}
