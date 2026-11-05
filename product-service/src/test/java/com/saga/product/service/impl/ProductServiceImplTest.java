package com.saga.product.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.product.dao.ProductRepository;
import com.saga.product.entity.ProductEntity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

  @Mock private ProductRepository productRepository;

  @InjectMocks private ProductServiceImpl productService;

  @ParameterizedTest(name = "stock {0} is reduced by one to {1}")
  @CsvSource({"10, 9", "1, 0"})
  void givenExistingProduct_whenBuyProduct_thenSavesQuantityReducedByOne(
      final int stock, final int remaining) {
    // given
    final UUID productId = UUID.randomUUID();
    when(this.productRepository.findById(productId))
        .thenReturn(
            Optional.of(
                ProductEntity.builder().id(productId).name("gadget").quantity(stock).build()));

    // when
    this.productService.buyProduct(productId);

    // then
    verify(this.productRepository)
        .save(ProductEntity.builder().id(productId).name("gadget").quantity(remaining).build());
  }

  @ParameterizedTest(name = "stock {0} is increased by one to {1}")
  @CsvSource({"9, 10", "0, 1"})
  void givenExistingProduct_whenRollbackProduct_thenSavesQuantityIncreasedByOne(
      final int stock, final int restored) {
    // given
    final UUID productId = UUID.randomUUID();
    when(this.productRepository.findById(productId))
        .thenReturn(
            Optional.of(
                ProductEntity.builder().id(productId).name("gadget").quantity(stock).build()));

    // when
    this.productService.rollbackProduct(productId);

    // then
    verify(this.productRepository)
        .save(ProductEntity.builder().id(productId).name("gadget").quantity(restored).build());
  }

  @Test
  void givenMissingProduct_whenBuyProduct_thenDoesNotSave() {
    // given
    final UUID productId = UUID.randomUUID();
    when(this.productRepository.findById(productId)).thenReturn(Optional.empty());

    // when
    this.productService.buyProduct(productId);

    // then
    verify(this.productRepository, never()).save(any());
  }

  @Test
  void givenMissingProduct_whenRollbackProduct_thenDoesNotSave() {
    // given
    final UUID productId = UUID.randomUUID();
    when(this.productRepository.findById(productId)).thenReturn(Optional.empty());

    // when
    this.productService.rollbackProduct(productId);

    // then
    verify(this.productRepository, never()).save(any());
  }
}
