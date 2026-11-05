package com.saga.product.kafka;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.saga.dto.product.ProductReservationFailedEvent;
import com.saga.dto.product.ProductReserveAction;
import com.saga.dto.product.ProductReservedEvent;
import com.saga.dto.product.ProductRollbackAction;
import com.saga.dto.product.ProductRollbackDoneEvent;
import com.saga.product.service.ProductService;
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
class ProductHandlerTest {

  private static final String PRODUCT_EVENTS_TOPIC = "product-events";

  @Mock private ProductService productService;

  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  @InjectMocks private ProductHandler productHandler;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(this.productHandler, "productEventsTopic", PRODUCT_EVENTS_TOPIC);
  }

  @Test
  void givenProductReserveAction_whenProductBought_thenSendsProductReservedEventWithQuantityOne() {
    // given
    final UUID productId = UUID.randomUUID();

    // when
    this.productHandler.handleProductAction(new ProductReserveAction(productId));

    // then
    verify(this.productService).buyProduct(productId);
    verify(this.kafkaTemplate).send(PRODUCT_EVENTS_TOPIC, new ProductReservedEvent(productId, 1));
  }

  @Test
  void givenProductReserveAction_whenBuyFails_thenSendsProductReservationFailedEvent() {
    // given
    final UUID productId = UUID.randomUUID();
    doThrow(new RuntimeException("out of stock")).when(this.productService).buyProduct(productId);

    // when
    this.productHandler.handleProductAction(new ProductReserveAction(productId));

    // then
    verify(this.kafkaTemplate)
        .send(
            eq(PRODUCT_EVENTS_TOPIC),
            eq(new ProductReservationFailedEvent(productId, "out of stock")));
  }

  @Test
  void givenProductRollbackAction_whenRollbackSucceeds_thenSendsProductRollbackDoneEvent() {
    // given
    final UUID productId = UUID.randomUUID();

    // when
    this.productHandler.handleProductAction(new ProductRollbackAction(productId));

    // then
    verify(this.productService).rollbackProduct(productId);
    verify(this.kafkaTemplate).send(PRODUCT_EVENTS_TOPIC, new ProductRollbackDoneEvent(productId));
  }

  @Test
  void givenProductRollbackActionThatFails_whenHandled_thenStillSendsProductRollbackDoneEvent() {
    // given
    final UUID productId = UUID.randomUUID();
    doThrow(new RuntimeException("rollback failed"))
        .when(this.productService)
        .rollbackProduct(productId);

    // when
    this.productHandler.handleProductAction(new ProductRollbackAction(productId));

    // then
    verify(this.kafkaTemplate).send(PRODUCT_EVENTS_TOPIC, new ProductRollbackDoneEvent(productId));
  }
}
