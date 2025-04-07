package com.saga.dto.payment;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessPaymentAction {

  private UUID orderId;
  private UUID productId;
  private Double amountToBePaid;
  private String creditCardNumber;
}
