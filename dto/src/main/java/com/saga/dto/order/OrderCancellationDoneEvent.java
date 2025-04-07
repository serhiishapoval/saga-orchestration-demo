package com.saga.dto.order;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancellationDoneEvent {

  private UUID orderId;
  private UUID productId;
}
