package com.saga.orchestrator.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KafkaEventHandlerTest {

  private static final String PRODUCT_ACTIONS_TOPIC = "product-actions";
  private static final String ORDER_ACTIONS_TOPIC = "order-actions";
  private static final String PAYMENT_ACTIONS_TOPIC = "payment-actions";

  private static final UUID PRODUCT_ID = UUID.randomUUID();
  private static final UUID ORDER_ID = UUID.randomUUID();
  private static final UUID PAYMENT_ID = UUID.randomUUID();
  private static final String FAILURE_DETAILS = "failure details";

  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  @InjectMocks private KafkaEventHandler kafkaEventHandler;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(
        this.kafkaEventHandler, "productActionsTopic", PRODUCT_ACTIONS_TOPIC);
    ReflectionTestUtils.setField(this.kafkaEventHandler, "orderActionsTopic", ORDER_ACTIONS_TOPIC);
    ReflectionTestUtils.setField(
        this.kafkaEventHandler, "paymentActionsTopic", PAYMENT_ACTIONS_TOPIC);
  }

  static Stream<Arguments> eventsAndExpectedActions() {
    return Stream.of(
        Arguments.of(
            (Consumer<KafkaEventHandler>)
                h -> h.handleProductEvent(new ProductReservedEvent(PRODUCT_ID, 1)),
            new CreateOrderAction(PRODUCT_ID, 1),
            ORDER_ACTIONS_TOPIC),
        Arguments.of(
            (Consumer<KafkaEventHandler>)
                h -> h.handleOrderEvent(new OrderCreationFailedEvent(PRODUCT_ID, FAILURE_DETAILS)),
            new ProductRollbackAction(PRODUCT_ID),
            PRODUCT_ACTIONS_TOPIC),
        Arguments.of(
            (Consumer<KafkaEventHandler>)
                h -> h.handleOrderEvent(new OrderCancellationDoneEvent(ORDER_ID, PRODUCT_ID)),
            new ProductRollbackAction(PRODUCT_ID),
            PRODUCT_ACTIONS_TOPIC),
        Arguments.of(
            (Consumer<KafkaEventHandler>)
                h ->
                    h.handlePaymentEvent(
                        new PaymentProcessedEvent(PAYMENT_ID, ORDER_ID, PRODUCT_ID)),
            new UpdateOrderToPaidStatusAction(ORDER_ID),
            ORDER_ACTIONS_TOPIC),
        Arguments.of(
            (Consumer<KafkaEventHandler>)
                h ->
                    h.handlePaymentEvent(
                        new PaymentProcessingFailedEvent(ORDER_ID, PRODUCT_ID, FAILURE_DETAILS)),
            new CancelOrderAction(ORDER_ID, PRODUCT_ID),
            ORDER_ACTIONS_TOPIC));
  }

  @ParameterizedTest(name = "event maps to {1} on topic {2}")
  @MethodSource("eventsAndExpectedActions")
  void givenEvent_whenHandled_thenSendsNextActionToExpectedTopic(
      final Consumer<KafkaEventHandler> handle, final Object expectedAction, final String topic) {
    // given

    // when
    handle.accept(this.kafkaEventHandler);

    // then
    verify(this.kafkaTemplate).send(topic, expectedAction);
  }

  static Stream<Consumer<KafkaEventHandler>> terminalEvents() {
    return Stream.of(
        h -> h.handleProductEvent(new ProductReservationFailedEvent(PRODUCT_ID, FAILURE_DETAILS)),
        h -> h.handleProductEvent(new ProductRollbackDoneEvent(PRODUCT_ID)),
        h -> h.handleOrderEvent(new OrderStatusToPaidUpdatedEvent(ORDER_ID)));
  }

  @ParameterizedTest
  @MethodSource("terminalEvents")
  void givenTerminalEvent_whenHandled_thenSendsNothing(final Consumer<KafkaEventHandler> handle) {
    // given

    // when
    handle.accept(this.kafkaEventHandler);

    // then
    verifyNoInteractions(this.kafkaTemplate);
  }

  @Test
  void givenOrderCreatedEvent_whenHandled_thenSendsProcessPaymentActionWithCardNumber() {
    // given
    final OrderCreatedEvent event = new OrderCreatedEvent(ORDER_ID, PRODUCT_ID, 2, 4.5D);
    final ArgumentCaptor<ProcessPaymentAction> captor =
        ArgumentCaptor.forClass(ProcessPaymentAction.class);

    // when
    this.kafkaEventHandler.handleOrderEvent(event);

    // then
    verify(this.kafkaTemplate).send(eq(PAYMENT_ACTIONS_TOPIC), captor.capture());
    final ProcessPaymentAction sent = captor.getValue();
    assertThat(sent.getCreditCardNumber()).matches("\\d{4} \\d{4} \\d{4} \\d{4}");
    final ProcessPaymentAction expected =
        new ProcessPaymentAction(ORDER_ID, PRODUCT_ID, 4.5D, sent.getCreditCardNumber());
    assertThat(sent).isEqualTo(expected);
  }
}
