package com.saga.dto.product;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductReservedEvent {

  private UUID productId;
  private Integer quantity;
}
