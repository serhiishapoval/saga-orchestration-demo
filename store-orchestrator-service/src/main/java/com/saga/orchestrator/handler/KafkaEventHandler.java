package com.saga.orchestrator.handler;

import com.saga.dto.order.CancelOrderAction;
import com.saga.dto.order.CreateOrderAction;
import com.saga.dto.order.OrderCancellationDoneEvent;
import com.saga.dto.order.OrderCreatedEvent;
import com.saga.dto.order.OrderCreationFailedEvent;
import com.saga.dto.order.OrderStatusToPaidUpdatedEvent;
import com.saga.dto.order.UpdateOrderToPaidStatusAction;
import com.saga.dto.payment.PaymentProcessedEvent;
import com.saga.dto.payment.PaymentProcessingFailedEvent;
import com.saga.dto.payment.ProcessPaymentAction;
import com.saga.dto.product.ProductReservationFailedEvent;
import com.saga.dto.product.ProductReservedEvent;
import com.saga.dto.product.ProductRollbackAction;
import com.saga.dto.product.ProductRollbackDoneEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import util.CreditCardUtils;

@Component
@RequiredArgsConstructor
@KafkaListener(topics = {"${kafka.topic.name.product.events}", "${kafka.topic.name.order.events}",
    "${kafka.topic.name.payment.events}"})
@Slf4j
public class KafkaEventHandler {

  @Value("${kafka.topic.name.product.actions}")
  private String productActionsTopic;

  @Value("${kafka.topic.name.order.actions}")
  private String orderActionsTopic;

  @Value("${kafka.topic.name.payment.actions}")
  private String paymentActionsTopic;

  private final KafkaTemplate<String, Object> kafkaTemplate;

  //PRODUCT'S HANDLERS
  // 1st call
  @KafkaHandler
  public void handleProductEvent(@Payload final ProductReservedEvent event) {
    log.info("handleProductEvent. Received ProductReservedEvent: {} ", event);

    final CreateOrderAction createOrderAction = new CreateOrderAction(event.getProductId(),
        event.getQuantity());
    this.kafkaTemplate.send(this.orderActionsTopic, createOrderAction);

    log.info("handleProductEvent. Sent CreateOrderAction: {} ", createOrderAction);
  }

  @KafkaHandler
  public void handleProductEvent(
      @Payload final ProductReservationFailedEvent event) {
    log.info("handleProductEvent. Received ProductReservationFailedEvent: {}", event);
    log.info("handleProductEvent. Product reservation failed for product id: {}",
        event.getProductId());
  }

  @KafkaHandler
  public void handleProductEvent(@Payload final ProductRollbackDoneEvent event) {
    log.info("handleProductEvent. Received ProductRollbackDoneEvent: {} ", event);
    log.info("handleProductEvent. Product rollback done for product id: {}", event.getProductId());
  }

  //ORDER'S HANDLERS
  // 2nd call
  @KafkaHandler
  public void handleOrderEvent(@Payload final OrderCreatedEvent event) {
    log.info("handleOrderEvent. Received OrderCreatedEvent: {} ", event);

    final ProcessPaymentAction action = new ProcessPaymentAction(event.getOrderId(),
        event.getProductId(), event.getTotalPrice(), CreditCardUtils.getCreditCardNumber());
    this.kafkaTemplate.send(this.paymentActionsTopic, action);

    log.info("handleOrderEvent. Sent ProcessPaymentAction: {} ", action);
  }

  @KafkaHandler
  public void handleOrderEvent(@Payload final OrderCreationFailedEvent event) {
    log.info("handleOrderEvent. Received OrderCreationFailedEvent: {} ", event);

    final ProductRollbackAction action = new ProductRollbackAction(
        event.getProductId());
    this.kafkaTemplate.send(this.productActionsTopic, action);

    log.info("handleOrderEvent. Sent ProductRollbackAction: {} ", action);
  }

  @KafkaHandler
  public void handleOrderEvent(@Payload final OrderCancellationDoneEvent event) {
    log.info("handleOrderEvent. Received OrderCancellationDoneEvent: {} ", event);
    final ProductRollbackAction action = new ProductRollbackAction(
        event.getProductId());
    this.kafkaTemplate.send(this.productActionsTopic, action);

    log.info("handleOrderEvent. Sent ProductRollbackAction: {} ", action);
  }

  @KafkaHandler
  public void handleOrderEvent(@Payload final OrderStatusToPaidUpdatedEvent event) {
    log.info("handleOrderEvent. Received OrderStatusToPaidUpdatedEvent: {} ", event);
    log.info("FLOW FINISHED SUCCESSFULLY");
  }

  //PAYMENT'S HANDLERS
  // 3rd call
  @KafkaHandler
  public void handlePaymentEvent(@Payload final PaymentProcessedEvent event) {
    log.info("handlePaymentEvent. Received PaymentProcessedEvent: {} ", event);

    UpdateOrderToPaidStatusAction action = new UpdateOrderToPaidStatusAction(event.getOrderId());
    this.kafkaTemplate.send(this.orderActionsTopic, action);

    log.info("handlePaymentEvent. Sent UpdateOrderToPaidStatusAction: {} ", action);
  }

  @KafkaHandler
  public void handlePaymentEvent(@Payload final PaymentProcessingFailedEvent event) {
    log.info("handlePaymentEvent. Received PaymentProcessingFailedEvent: {} ", event);

    final CancelOrderAction action = new CancelOrderAction(event.getOrderId(),
        event.getProductId());
    this.kafkaTemplate.send(this.orderActionsTopic, action);

    log.info("handlePaymentEvent. Sent CancelOrderAction: {} ", action);
  }

}
