package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReplaceWarehouseUseCaseTest {

  private WarehouseStore warehouseStore;
  private LocationResolver locationResolver;
  private ReplaceWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    warehouseStore = mock(WarehouseStore.class);
    locationResolver = mock(LocationResolver.class);
    useCase = new ReplaceWarehouseUseCase(warehouseStore, locationResolver);
  }

  @Test
  void replace_archivesOldAndCreatesNew() {
    var oldWarehouse = activeWarehouse("MWH.001", "ZWOLLE-001", 100, 10);
    when(warehouseStore.findByBusinessUnitCode("MWH.001")).thenReturn(oldWarehouse);
    when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
        .thenReturn(new Location("ZWOLLE-001", 3, 40));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "ZWOLLE-001";
    newWarehouse.capacity = 30;
    newWarehouse.stock = 10; // must match old stock

    useCase.replace(newWarehouse);

    assertNotNull(oldWarehouse.archivedAt);
    assertNull(newWarehouse.archivedAt);
    assertNotNull(newWarehouse.createdAt);
    verify(warehouseStore).update(oldWarehouse);
    verify(warehouseStore).create(newWarehouse);
  }

  @Test
  void replace_failsWith404WhenOldWarehouseNotFound() {
    when(warehouseStore.findByBusinessUnitCode("MWH.MISSING")).thenReturn(null);

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.MISSING";
    newWarehouse.location = "ZWOLLE-001";
    newWarehouse.capacity = 30;
    newWarehouse.stock = 10;

    var ex = assertThrows(WebApplicationException.class, () -> useCase.replace(newWarehouse));
    assertEquals(404, ex.getResponse().getStatus());
  }

  @Test
  void replace_failsWith400WhenNewCapacityCannotAccommodateOldStock() {
    var oldWarehouse = activeWarehouse("MWH.001", "ZWOLLE-001", 100, 25);
    when(warehouseStore.findByBusinessUnitCode("MWH.001")).thenReturn(oldWarehouse);
    when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
        .thenReturn(new Location("ZWOLLE-001", 3, 40));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "ZWOLLE-001";
    newWarehouse.capacity = 20; // less than old stock of 25
    newWarehouse.stock = 25;

    var ex = assertThrows(WebApplicationException.class, () -> useCase.replace(newWarehouse));
    assertEquals(400, ex.getResponse().getStatus());
  }

  @Test
  void replace_failsWith400WhenStockDoesNotMatchOldWarehouse() {
    var oldWarehouse = activeWarehouse("MWH.001", "ZWOLLE-001", 100, 10);
    when(warehouseStore.findByBusinessUnitCode("MWH.001")).thenReturn(oldWarehouse);
    when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
        .thenReturn(new Location("ZWOLLE-001", 3, 40));

    var newWarehouse = new Warehouse();
    newWarehouse.businessUnitCode = "MWH.001";
    newWarehouse.location = "ZWOLLE-001";
    newWarehouse.capacity = 30;
    newWarehouse.stock = 5; // does not match old stock of 10

    var ex = assertThrows(WebApplicationException.class, () -> useCase.replace(newWarehouse));
    assertEquals(400, ex.getResponse().getStatus());
  }

  private Warehouse activeWarehouse(String buc, String location, int capacity, int stock) {
    var w = new Warehouse();
    w.businessUnitCode = buc;
    w.location = location;
    w.capacity = capacity;
    w.stock = stock;
    return w;
  }
}
