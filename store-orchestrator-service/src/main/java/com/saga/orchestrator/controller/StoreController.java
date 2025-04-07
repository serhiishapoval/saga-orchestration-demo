package com.saga.orchestrator.controller;

import com.saga.dto.product.ProductReserveAction;
import com.saga.orchestrator.dto.BuySomethingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/store")
@RequiredArgsConstructor
public class StoreController {

  @Value("${kafka.topic.name.product.actions}")
  private String productActionsTopic;

  private final KafkaTemplate<String, Object> kafkaTemplate;

  @PostMapping("/buy")
  public ResponseEntity<Void> buySomething(@RequestBody final BuySomethingRequest request) {
    log.info("buySomething. Received request to buy a product by ID: {}", request);
    final ProductReserveAction productReserveAction = new ProductReserveAction(
        request.getProductId());

    this.kafkaTemplate.send(this.productActionsTopic, productReserveAction);
    log.info("buySomething. Sent ProductReserveAction: {}", productReserveAction);
    return ResponseEntity.ok().build();
  }
}
