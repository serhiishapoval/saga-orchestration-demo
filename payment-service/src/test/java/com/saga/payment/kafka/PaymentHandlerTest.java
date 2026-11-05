package com.saga.payment.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.dto.payment.PaymentProcessedEvent;
import com.saga.dto.payment.PaymentProcessingFailedEvent;
import com.saga.dto.payment.ProcessPaymentAction;
import com.saga.payment.service.PaymentService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentHandlerTest {

  private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

  @Mock private PaymentService paymentService;

  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  @InjectMocks private PaymentHandler paymentHandler;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(this.paymentHandler, "paymentEventsTopic", PAYMENT_EVENTS_TOPIC);
  }

  @Test
  void givenProcessPaymentAction_whenPaymentSucceeds_thenSendsPaymentProcessedEvent() {
    // given
    final UUID orderId = UUID.randomUUID();
    final UUID productId = UUID.randomUUID();
    final UUID paymentId = UUID.randomUUID();
    final ProcessPaymentAction action =
        new ProcessPaymentAction(orderId, productId, 3.0D, "1234 5678 9012 3456");
    when(this.paymentService.pay(orderId, productId, 3.0D, "1234 5678 9012 3456"))
        .thenReturn(paymentId);

    // when
    this.paymentHandler.handlePaymentAction(action);

    // then
    verify(this.kafkaTemplate)
        .send(PAYMENT_EVENTS_TOPIC, new PaymentProcessedEvent(paymentId, orderId, productId));
  }

  @Test
  void givenProcessPaymentAction_whenPaymentFails_thenSendsPaymentProcessingFailedEvent() {
    // given
    final UUID orderId = UUID.randomUUID();
    final UUID productId = UUID.randomUUID();
    final ProcessPaymentAction action =
        new ProcessPaymentAction(orderId, productId, 3.0D, "1234 5678 9012 3456");
    doThrow(new RuntimeException("insufficient funds"))
        .when(this.paymentService)
        .pay(any(), any(), anyDouble(), anyString());

    // when
    this.paymentHandler.handlePaymentAction(action);

    // then
    verify(this.kafkaTemplate)
        .send(
            eq(PAYMENT_EVENTS_TOPIC),
            eq(new PaymentProcessingFailedEvent(orderId, productId, "insufficient funds")));
  }
}
