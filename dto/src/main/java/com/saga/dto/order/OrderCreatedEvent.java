package com.saga.dto.order;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {

  private UUID orderId;
  private UUID productId;
  private Integer quantity;
  private Double totalPrice;
}
