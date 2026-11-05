package com.saga.payment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.saga.payment.dao.PaymentRepository;
import com.saga.payment.entity.PaymentEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

  @Mock private PaymentRepository paymentRepository;

  @InjectMocks private PaymentServiceImpl paymentService;

  @Test
  void givenPaymentDetails_whenPay_thenSavesPaymentAndReturnsSavedPaymentId() {
    // given
    final UUID orderId = UUID.randomUUID();
    final UUID productId = UUID.randomUUID();
    final ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);

    // when
    final UUID paymentId = this.paymentService.pay(orderId, productId, 3.0D, "1234 5678 9012 3456");

    // then
    verify(this.paymentRepository).save(captor.capture());
    final PaymentEntity expected = new PaymentEntity(paymentId, productId, orderId, 3.0D);
    assertThat(captor.getValue()).isEqualTo(expected);
  }
}
