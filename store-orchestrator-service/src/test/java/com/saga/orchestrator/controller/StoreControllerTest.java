package com.saga.orchestrator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.saga.dto.product.ProductReserveAction;
import com.saga.orchestrator.dto.BuySomethingRequest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoreControllerTest {

  private static final String PRODUCT_ACTIONS_TOPIC = "product-actions";

  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  @InjectMocks private StoreController storeController;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(
        this.storeController, "productActionsTopic", PRODUCT_ACTIONS_TOPIC);
  }

  @Test
  void givenBuyRequest_whenBuySomething_thenSendsProductReserveActionAndReturnsOk() {
    // given
    final UUID productId = UUID.randomUUID();
    final BuySomethingRequest request = new BuySomethingRequest(productId);

    // when
    final ResponseEntity<Void> response = this.storeController.buySomething(request);

    // then
    verify(this.kafkaTemplate)
        .send(eq(PRODUCT_ACTIONS_TOPIC), eq(new ProductReserveAction(productId)));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
