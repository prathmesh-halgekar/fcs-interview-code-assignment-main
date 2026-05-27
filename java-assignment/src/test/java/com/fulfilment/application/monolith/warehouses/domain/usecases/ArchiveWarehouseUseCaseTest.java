package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArchiveWarehouseUseCaseTest {

  private WarehouseStore warehouseStore;
  private ArchiveWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    warehouseStore = mock(WarehouseStore.class);
    useCase = new ArchiveWarehouseUseCase(warehouseStore);
  }

  @Test
  void archive_setsArchivedAtAndCallsUpdate() {
    var warehouse = activeWarehouse("MWH.001");

    useCase.archive(warehouse);

    assertNotNull(warehouse.archivedAt);
    verify(warehouseStore).update(warehouse);
  }

  @Test
  void archive_isIdempotentWhenAlreadyArchived() {
    var warehouse = activeWarehouse("MWH.001");
    warehouse.archivedAt = LocalDateTime.now().minusDays(1); // already archived

    useCase.archive(warehouse);

    // update should NOT be called again for an already-archived warehouse
    verify(warehouseStore, never()).update(warehouse);
  }

  private Warehouse activeWarehouse(String buc) {
    var warehouse = new Warehouse();
    warehouse.businessUnitCode = buc;
    warehouse.location = "ZWOLLE-001";
    warehouse.capacity = 100;
    warehouse.stock = 10;
    return warehouse;
  }
}
