package com.saga.order.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.dto.order.CancelOrderAction;
import com.saga.dto.order.CreateOrderAction;
import com.saga.dto.order.OrderCancellationDoneEvent;
import com.saga.dto.order.OrderCreatedEvent;
import com.saga.dto.order.OrderCreationFailedEvent;
import com.saga.dto.order.OrderStatusToPaidUpdatedEvent;
import com.saga.dto.order.UpdateOrderToPaidStatusAction;
import com.saga.order.entity.OrderEntity;
import com.saga.order.entity.OrderStatus;
import com.saga.order.service.OrderService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderHandlerTest {

  private static final String ORDER_EVENTS_TOPIC = "order-events";

  @Mock private OrderService orderService;

  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  @InjectMocks private OrderHandler orderHandler;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(this.orderHandler, "orderEventsTopic", ORDER_EVENTS_TOPIC);
  }

  @Test
  void givenCreateOrderAction_whenOrderCreated_thenSendsOrderCreatedEvent() {
    // given
    final UUID productId = UUID.randomUUID();
    final UUID orderId = UUID.randomUUID();
    final CreateOrderAction action = new CreateOrderAction(productId, 2);
    when(this.orderService.createOrder(productId, 2))
        .thenReturn(new OrderEntity(orderId, productId, 2, OrderStatus.NEW.getStatus()));
    final ArgumentCaptor<OrderCreatedEvent> captor =
        ArgumentCaptor.forClass(OrderCreatedEvent.class);

    // when
    this.orderHandler.handleOrderAction(action);

    // then
    verify(this.kafkaTemplate).send(eq(ORDER_EVENTS_TOPIC), captor.capture());
    final OrderCreatedEvent sent = captor.getValue();
    assertThat(sent.getTotalPrice()).isBetween(0.0D, 1.0D);
    final OrderCreatedEvent expected =
        new OrderCreatedEvent(orderId, productId, 2, sent.getTotalPrice());
    assertThat(sent).isEqualTo(expected);
  }

  @Test
  void givenCreateOrderActionThatFails_whenHandled_thenExceptionPropagatesAndNothingIsSent() {
    // given
    final UUID productId = UUID.randomUUID();
    final CreateOrderAction action = new CreateOrderAction(productId, 2);
    when(this.orderService.createOrder(productId, 2)).thenThrow(new RuntimeException("db down"));

    // when / then
    assertThatThrownBy(() -> this.orderHandler.handleOrderAction(action))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("db down");
    verifyNoInteractions(this.kafkaTemplate);
  }

  @Test
  void givenOrderCreatedEventFailsToSend_whenHandled_thenSendsOrderCreationFailedEvent() {
    // given
    final UUID productId = UUID.randomUUID();
    final UUID orderId = UUID.randomUUID();
    final CreateOrderAction action = new CreateOrderAction(productId, 2);
    when(this.orderService.createOrder(productId, 2))
        .thenReturn(new OrderEntity(orderId, productId, 2, OrderStatus.NEW.getStatus()));
    when(this.kafkaTemplate.send(eq(ORDER_EVENTS_TOPIC), any(OrderCreatedEvent.class)))
        .thenThrow(new RuntimeException("kafka down"));

    // when
    this.orderHandler.handleOrderAction(action);

    // then
    final OrderCreationFailedEvent expected = new OrderCreationFailedEvent(orderId, "kafka down");
    verify(this.kafkaTemplate).send(ORDER_EVENTS_TOPIC, expected);
  }

  @ParameterizedTest(name = "cancelOrder throws = {0}")
  @ValueSource(booleans = {false, true})
  void givenCancelOrderAction_whenHandled_thenAlwaysSendsOrderCancellationDoneEvent(
      final boolean cancelFails) {
    // given
    final UUID orderId = UUID.randomUUID();
    final UUID productId = UUID.randomUUID();
    final CancelOrderAction action = new CancelOrderAction(orderId, productId);
    if (cancelFails) {
      doThrow(new RuntimeException("cancel failed")).when(this.orderService).cancelOrder(orderId);
    }

    // when
    this.orderHandler.handleOrderAction(action);

    // then
    verify(this.orderService).cancelOrder(orderId);
    verify(this.kafkaTemplate)
        .send(ORDER_EVENTS_TOPIC, new OrderCancellationDoneEvent(orderId, productId));
  }

  @ParameterizedTest(name = "payOrder throws = {0}")
  @ValueSource(booleans = {false, true})
  void givenUpdateOrderToPaidStatusAction_whenHandled_thenAlwaysSendsOrderStatusToPaidUpdatedEvent(
      final boolean payFails) {
    // given
    final UUID orderId = UUID.randomUUID();
    final UpdateOrderToPaidStatusAction action = new UpdateOrderToPaidStatusAction(orderId);
    if (payFails) {
      doThrow(new RuntimeException("pay failed")).when(this.orderService).payOrder(orderId);
    }

    // when
    this.orderHandler.handleOrderAction(action);

    // then
    verify(this.orderService).payOrder(orderId);
    verify(this.kafkaTemplate).send(ORDER_EVENTS_TOPIC, new OrderStatusToPaidUpdatedEvent(orderId));
  }
}
