package com.fulfilment.application.monolith.products.domain.usecases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fulfilment.application.monolith.products.adapters.database.DbProductWarehouseStoreAssociation;
import com.fulfilment.application.monolith.products.adapters.database.ProductWarehouseStoreRepository;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnassignProductFromWarehouseForStoreUseCaseTest {

  private ProductWarehouseStoreRepository associationRepository;
  private UnassignProductFromWarehouseForStoreUseCase useCase;

  @BeforeEach
  void setUp() {
    associationRepository = mock(ProductWarehouseStoreRepository.class);
    useCase = new UnassignProductFromWarehouseForStoreUseCase();
    useCase.associationRepository = associationRepository;
  }

  @Test
  void unassign_success_removesAssociation() {
    when(associationRepository.findByKey(1L, "MWH.001", 1L))
        .thenReturn(new DbProductWarehouseStoreAssociation(1L, "MWH.001", 1L, LocalDateTime.now()));

    useCase.unassign(1L, "MWH.001", 1L);

    verify(associationRepository).remove(1L, "MWH.001", 1L);
  }

  @Test
  void unassign_associationNotFound_throws404() {
    when(associationRepository.findByKey(1L, "MWH.001", 1L)).thenReturn(null);

    var ex =
        assertThrows(
            WebApplicationException.class, () -> useCase.unassign(1L, "MWH.001", 1L));
    assertEquals(404, ex.getResponse().getStatus());
  }

  @Test
  void unassign_nullProductId_throws400() {
    var ex =
        assertThrows(
            WebApplicationException.class, () -> useCase.unassign(null, "MWH.001", 1L));
    assertEquals(400, ex.getResponse().getStatus());
  }

  @Test
  void unassign_blankWarehouseCode_throws400() {
    var ex =
        assertThrows(
            WebApplicationException.class, () -> useCase.unassign(1L, "  ", 1L));
    assertEquals(400, ex.getResponse().getStatus());
  }
}
