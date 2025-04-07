package com.saga.payment.kafka;

import com.saga.dto.payment.PaymentProcessedEvent;
import com.saga.dto.payment.PaymentProcessingFailedEvent;
import com.saga.dto.payment.ProcessPaymentAction;
import com.saga.payment.service.PaymentService;
import java.util.UUID;
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
@KafkaListener(topics = {"${kafka.topic.name.payment.actions}"})
@Slf4j
public class PaymentHandler {

  @Value("${kafka.topic.name.payment.events}")
  private String paymentEventsTopic;

  private final PaymentService paymentService;
  private final KafkaTemplate<String, Object> kafkaTemplate;

  @KafkaHandler
  public void handlePaymentAction(@Payload final ProcessPaymentAction action) {
    log.info("handlePaymentAction. Received ProcessPaymentAction: {} ", action);
    try {
      final UUID paymentId = this.paymentService.pay(action.getOrderId(), action.getProductId(),
          action.getAmountToBePaid(), action.getCreditCardNumber());
      PaymentProcessedEvent paymentProcessedEvent = new PaymentProcessedEvent(paymentId,
          action.getOrderId(), action.getProductId());

      this.kafkaTemplate.send(this.paymentEventsTopic, paymentProcessedEvent);

      log.info("handlePaymentAction. Sent PaymentProcessedEvent: {} ", paymentProcessedEvent);
    } catch (Exception e) {
      final PaymentProcessingFailedEvent paymentProcessingFailedEvent = new PaymentProcessingFailedEvent(
          action.getOrderId(), action.getProductId(), e.getMessage());

      this.kafkaTemplate.send(this.paymentEventsTopic, paymentProcessingFailedEvent);

      log.info("handlePaymentAction. Sent PaymentProcessingFailedEvent: {} ",
          paymentProcessingFailedEvent);
    }

  }
}
