package com.saga.dto.payment;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProcessingFailedEvent {

  private UUID orderId;
  private UUID productId;
  private String details;
}
