package com.fulfilment.application.monolith.products.domain.usecases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.products.adapters.database.DbProductWarehouseStoreAssociation;
import com.fulfilment.application.monolith.products.adapters.database.ProductWarehouseStoreRepository;
import com.fulfilment.application.monolith.stores.Store;
import com.fulfilment.application.monolith.stores.StoreRepository;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssignProductToWarehouseForStoreUseCaseTest {

  private ProductRepository productRepository;
  private WarehouseRepository warehouseRepository;
  private StoreRepository storeRepository;
  private ProductWarehouseStoreRepository associationRepository;
  private AssignProductToWarehouseForStoreUseCase useCase;

  @BeforeEach
  void setUp() {
    productRepository = mock(ProductRepository.class);
    warehouseRepository = mock(WarehouseRepository.class);
    storeRepository = mock(StoreRepository.class);
    associationRepository = mock(ProductWarehouseStoreRepository.class);

    useCase = new AssignProductToWarehouseForStoreUseCase();
    useCase.productRepository = productRepository;
    useCase.warehouseRepository = warehouseRepository;
    useCase.storeRepository = storeRepository;
    useCase.associationRepository = associationRepository;
  }

  @Test
  void assign_success_createsAssociation() {
    when(productRepository.findById(1L)).thenReturn(new Product("TONSTAD"));
    when(warehouseRepository.findByBusinessUnitCode("MWH.001")).thenReturn(new Warehouse());
    when(storeRepository.findById(1L)).thenReturn(new Store("TONSTAD"));
    when(associationRepository.findByKey(1L, "MWH.001", 1L)).thenReturn(null);
    when(associationRepository.countWarehousesForProductInStore(1L, 1L)).thenReturn(0L);
    when(associationRepository.countWarehousesForStore(1L)).thenReturn(0L);
    when(associationRepository.countProductTypesInWarehouse("MWH.001")).thenReturn(0L);

    useCase.assign(1L, "MWH.001", 1L);

    verify(associationRepository).create(any(DbProductWarehouseStoreAssociation.class));
  }

  @Test
  void assign_productNotFound_throws404() {
    when(productRepository.findById(99L)).thenReturn(null);

    var ex =
        assertThrows(WebApplicationException.class, () -> useCase.assign(99L, "MWH.001", 1L));
    assertEquals(404, ex.getResponse().getStatus());
  }

  @Test
  void assign_warehouseNotFound_throws404() {
    when(productRepository.findById(1L)).thenReturn(new Product("TONSTAD"));
    when(warehouseRepository.findByBusinessUnitCode("MWH.UNKNOWN")).thenReturn(null);

    var ex =
        assertThrows(
            WebApplicationException.class, () -> useCase.assign(1L, "MWH.UNKNOWN", 1L));
    assertEquals(404, ex.getResponse().getStatus());
  }

  @Test
  void assign_storeNotFound_throws404() {
    when(productRepository.findById(1L)).thenReturn(new Product("TONSTAD"));
    when(warehouseRepository.findByBusinessUnitCode("MWH.001")).thenReturn(new Warehouse());
    when(storeRepository.findById(99L)).thenReturn(null);

    var ex =
        assertThrows(WebApplicationException.class, () -> useCase.assign(1L, "MWH.001", 99L));
    assertEquals(404, ex.getResponse().getStatus());
  }

  @Test
  void assign_duplicateAssociation_throws409() {
    when(productRepository.findById(1L)).thenReturn(new Product("TONSTAD"));
    when(warehouseRepository.findByBusinessUnitCode("MWH.001")).thenReturn(new Warehouse());
    when(storeRepository.findById(1L)).thenReturn(new Store("TONSTAD"));
    when(associationRepository.findByKey(1L, "MWH.001", 1L))
        .thenReturn(new DbProductWarehouseStoreAssociation(1L, "MWH.001", 1L, null));

    var ex =
        assertThrows(WebApplicationException.class, () -> useCase.assign(1L, "MWH.001", 1L));
    assertEquals(409, ex.getResponse().getStatus());
  }

  @Test
  void assign_exceedsMaxWarehousesPerProductPerStore_throws409() {
    when(productRepository.findById(1L)).thenReturn(new Product("TONSTAD"));
    when(warehouseRepository.findByBusinessUnitCode("MWH.003")).thenReturn(new Warehouse());
    when(storeRepository.findById(1L)).thenReturn(new Store("TONSTAD"));
    when(associationRepository.findByKey(1L, "MWH.003", 1L)).thenReturn(null);
    when(associationRepository.countWarehousesForProductInStore(1L, 1L)).thenReturn(2L);

    var ex =
        assertThrows(WebApplicationException.class, () -> useCase.assign(1L, "MWH.003", 1L));
    assertEquals(409, ex.getResponse().getStatus());
  }

  @Test
  void assign_exceedsMaxWarehousesPerStore_throws409() {
    when(productRepository.findById(1L)).thenReturn(new Product("TONSTAD"));
    when(warehouseRepository.findByBusinessUnitCode("MWH.003")).thenReturn(new Warehouse());
    when(storeRepository.findById(1L)).thenReturn(new Store("TONSTAD"));
    when(associationRepository.findByKey(1L, "MWH.003", 1L)).thenReturn(null);
    when(associationRepository.countWarehousesForProductInStore(1L, 1L)).thenReturn(0L);
    when(associationRepository.countWarehousesForStore(1L)).thenReturn(3L);

    var ex =
        assertThrows(WebApplicationException.class, () -> useCase.assign(1L, "MWH.003", 1L));
    assertEquals(409, ex.getResponse().getStatus());
  }

  @Test
  void assign_exceedsMaxProductTypesPerWarehouse_throws409() {
    when(productRepository.findById(1L)).thenReturn(new Product("TONSTAD"));
    when(warehouseRepository.findByBusinessUnitCode("MWH.001")).thenReturn(new Warehouse());
    when(storeRepository.findById(2L)).thenReturn(new Store("KALLAX"));
    when(associationRepository.findByKey(1L, "MWH.001", 2L)).thenReturn(null);
    when(associationRepository.countWarehousesForProductInStore(1L, 2L)).thenReturn(0L);
    when(associationRepository.countWarehousesForStore(2L)).thenReturn(0L);
    when(associationRepository.countProductTypesInWarehouse("MWH.001")).thenReturn(5L);

    var ex =
        assertThrows(WebApplicationException.class, () -> useCase.assign(1L, "MWH.001", 2L));
    assertEquals(409, ex.getResponse().getStatus());
  }

  @Test
  void assign_nullProductId_throws400() {
    var ex =
        assertThrows(WebApplicationException.class, () -> useCase.assign(null, "MWH.001", 1L));
    assertEquals(400, ex.getResponse().getStatus());
  }
}
