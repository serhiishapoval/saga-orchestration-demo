package com.saga.dto.payment;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProcessedEvent {

  private UUID paymentId;
  private UUID orderId;
  private UUID productId;
}
