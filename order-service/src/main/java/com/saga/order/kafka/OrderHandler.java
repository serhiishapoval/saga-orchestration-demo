package com.saga.order.kafka;

import com.saga.dto.order.CancelOrderAction;
import com.saga.dto.order.CreateOrderAction;
import com.saga.dto.order.OrderCancellationDoneEvent;
import com.saga.dto.order.OrderCreatedEvent;
import com.saga.dto.order.OrderCreationFailedEvent;
import com.saga.dto.order.OrderStatusToPaidUpdatedEvent;
import com.saga.dto.order.UpdateOrderToPaidStatusAction;
import com.saga.order.entity.OrderEntity;
import com.saga.order.service.OrderService;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@KafkaListener(topics = {"${kafka.topic.name.order.actions}"})
@Slf4j
public class OrderHandler {

  @Value("${kafka.topic.name.order.events}")
  private String orderEventsTopic;

  private final OrderService orderService;

  private final KafkaTemplate<String, Object> kafkaTemplate;

  @KafkaHandler
  public void handleOrderAction(@Payload final CreateOrderAction action) {
    log.info("handleOrderAction. Received CreateOrderAction: {} ", action);
    final OrderEntity createdOrder = this.orderService.createOrder(action.getProductId(),
        action.getQuantity());
    try {
      final OrderCreatedEvent orderCreatedEvent = new OrderCreatedEvent(createdOrder.getOrderId(),
          action.getProductId(),
          createdOrder.getQuantity(), new Random().nextDouble());

      this.kafkaTemplate.send(this.orderEventsTopic, orderCreatedEvent);

      log.info("handleOrderAction. Sent OrderCreatedEvent: {} ", orderCreatedEvent);
    } catch (Exception e) {

      OrderCreationFailedEvent orderCreationFailedEvent = new OrderCreationFailedEvent(
          createdOrder.getOrderId(), e.getMessage());
      this.kafkaTemplate.send(this.orderEventsTopic, orderCreationFailedEvent);

      log.info("handleOrderAction. Sent OrderCreationFailedEvent: {} ", orderCreationFailedEvent);
    }
  }

  @KafkaHandler
  public void handleOrderAction(@Payload final CancelOrderAction action) {
    log.info("handleOrderAction. Received CancelOrderAction: {} ", action);
    try {
      this.orderService.cancelOrder(action.getOrderId());
    } catch (Exception e) {
      //HERE WE SHOULD THROW A RETRYABLE EXCEPTION
    }
    final OrderCancellationDoneEvent orderCancellationDoneEvent = new OrderCancellationDoneEvent(
        action.getOrderId(), action.getProductId());

    this.kafkaTemplate.send(this.orderEventsTopic, orderCancellationDoneEvent);

    log.info("handleOrderAction. Sent OrderCancellationDoneEvent: {} ", orderCancellationDoneEvent);
  }

  @KafkaHandler
  public void handleOrderAction(@Payload final UpdateOrderToPaidStatusAction action) {
    log.info("handleOrderAction. Received UpdateOrderToPaidStatusAction: {} ", action);

    try {
      this.orderService.payOrder(action.getOrderId());
    } catch (Exception e) {
      //HERE WE SHOULD THROW A RETRYABLE EXCEPTION
    }

    final OrderStatusToPaidUpdatedEvent orderStatusToPaidUpdatedEvent = new OrderStatusToPaidUpdatedEvent(
        action.getOrderId());

    this.kafkaTemplate.send(this.orderEventsTopic, orderStatusToPaidUpdatedEvent);
    log.info("handleOrderAction. Sent OrderStatusToPaidUpdatedEvent: {} ",
        orderStatusToPaidUpdatedEvent);
  }
}
