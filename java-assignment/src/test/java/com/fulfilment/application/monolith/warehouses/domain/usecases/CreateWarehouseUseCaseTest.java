package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.ws.rs.WebApplicationException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateWarehouseUseCaseTest {

  private WarehouseStore warehouseStore;
  private LocationResolver locationResolver;
  private CreateWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    warehouseStore = mock(WarehouseStore.class);
    locationResolver = mock(LocationResolver.class);
    useCase = new CreateWarehouseUseCase(warehouseStore, locationResolver);
  }

  @Test
  void create_succeedsWhenAllValidationsPass() {
    var warehouse = validWarehouse("MWH.NEW", "ZWOLLE-001", 30, 5);
    when(warehouseStore.findByBusinessUnitCode("MWH.NEW")).thenReturn(null);
    when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
        .thenReturn(new Location("ZWOLLE-001", 3, 40));
    when(warehouseStore.getAll()).thenReturn(Collections.emptyList());

    assertDoesNotThrow(() -> useCase.create(warehouse));
    verify(warehouseStore).create(warehouse);
  }

  @Test
  void create_failsWith409WhenBucAlreadyExists() {
    var warehouse = validWarehouse("MWH.001", "ZWOLLE-001", 30, 5);
    when(warehouseStore.findByBusinessUnitCode("MWH.001")).thenReturn(new Warehouse());

    var ex = assertThrows(WebApplicationException.class, () -> useCase.create(warehouse));
    assertEquals(409, ex.getResponse().getStatus());
    verify(warehouseStore, never()).create(warehouse);
  }

  @Test
  void create_failsWith404WhenLocationNotFound() {
    var warehouse = validWarehouse("MWH.NEW", "UNKNOWN-001", 30, 5);
    when(warehouseStore.findByBusinessUnitCode("MWH.NEW")).thenReturn(null);
    when(locationResolver.resolveByIdentifier("UNKNOWN-001")).thenReturn(null);

    var ex = assertThrows(WebApplicationException.class, () -> useCase.create(warehouse));
    assertEquals(404, ex.getResponse().getStatus());
    verify(warehouseStore, never()).create(warehouse);
  }

  @Test
  void create_failsWith409WhenMaxWarehousesAtLocationReached() {
    var warehouse = validWarehouse("MWH.NEW", "ZWOLLE-001", 30, 5);
    when(warehouseStore.findByBusinessUnitCode("MWH.NEW")).thenReturn(null);
    when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
        .thenReturn(new Location("ZWOLLE-001", 1, 40));
    // One warehouse already exists at this location (maxNumberOfWarehouses = 1)
    var existing = new Warehouse();
    existing.location = "ZWOLLE-001";
    when(warehouseStore.getAll()).thenReturn(List.of(existing));

    var ex = assertThrows(WebApplicationException.class, () -> useCase.create(warehouse));
    assertEquals(409, ex.getResponse().getStatus());
    verify(warehouseStore, never()).create(warehouse);
  }

  @Test
  void create_failsWith400WhenCapacityExceedsLocationMax() {
    var warehouse = validWarehouse("MWH.NEW", "ZWOLLE-001", 50, 5);
    when(warehouseStore.findByBusinessUnitCode("MWH.NEW")).thenReturn(null);
    when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
        .thenReturn(new Location("ZWOLLE-001", 3, 40)); // maxCapacity=40, warehouse.capacity=50
    when(warehouseStore.getAll()).thenReturn(Collections.emptyList());

    var ex = assertThrows(WebApplicationException.class, () -> useCase.create(warehouse));
    assertEquals(400, ex.getResponse().getStatus());
    verify(warehouseStore, never()).create(warehouse);
  }

  @Test
  void create_failsWith400WhenStockExceedsCapacity() {
    var warehouse = validWarehouse("MWH.NEW", "ZWOLLE-001", 30, 35);
    when(warehouseStore.findByBusinessUnitCode("MWH.NEW")).thenReturn(null);
    when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
        .thenReturn(new Location("ZWOLLE-001", 3, 40));
    when(warehouseStore.getAll()).thenReturn(Collections.emptyList());

    var ex = assertThrows(WebApplicationException.class, () -> useCase.create(warehouse));
    assertEquals(400, ex.getResponse().getStatus());
    verify(warehouseStore, never()).create(warehouse);
  }

  private Warehouse validWarehouse(String buc, String location, int capacity, int stock) {
    var w = new Warehouse();
    w.businessUnitCode = buc;
    w.location = location;
    w.capacity = capacity;
    w.stock = stock;
    return w;
  }
}
