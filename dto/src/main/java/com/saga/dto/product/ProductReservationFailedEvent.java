package com.saga.dto.product;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductReservationFailedEvent {

  private UUID productId;
  private String details;
}
