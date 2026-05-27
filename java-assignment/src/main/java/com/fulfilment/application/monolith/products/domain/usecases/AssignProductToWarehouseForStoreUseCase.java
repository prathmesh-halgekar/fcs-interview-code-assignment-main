package com.fulfilment.application.monolith.products.domain.usecases;

import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.products.adapters.database.DbProductWarehouseStoreAssociation;
import com.fulfilment.application.monolith.products.adapters.database.ProductWarehouseStoreRepository;
import com.fulfilment.application.monolith.stores.StoreRepository;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDateTime;
import org.jboss.logging.Logger;

/**
 * Use case for assigning a product to a warehouse as a fulfilment unit for a specific store.
 *
 * <p>Enforces three cardinality constraints before persisting the association:
 *
 * <ol>
 *   <li>A product can be fulfilled by at most 2 warehouses per store (HTTP 409 if exceeded).
 *   <li>A store can be served by at most 3 warehouses (HTTP 409 if exceeded).
 *   <li>A warehouse can store at most 5 distinct product types (HTTP 409 if exceeded).
 * </ol>
 *
 * <p>Input validations (HTTP 400) and existence checks (HTTP 404) are performed first.
 * Duplicate assignments return HTTP 409.
 */
@ApplicationScoped
public class AssignProductToWarehouseForStoreUseCase {

  private static final Logger LOGGER =
      Logger.getLogger(AssignProductToWarehouseForStoreUseCase.class.getName());

  private static final int MAX_WAREHOUSES_PER_PRODUCT_PER_STORE = 2;
  private static final int MAX_WAREHOUSES_PER_STORE = 3;
  private static final int MAX_PRODUCT_TYPES_PER_WAREHOUSE = 5;

  @Inject ProductRepository productRepository;
  @Inject WarehouseRepository warehouseRepository;
  @Inject StoreRepository storeRepository;
  @Inject ProductWarehouseStoreRepository associationRepository;

  /**
   * Assigns a product to a warehouse for fulfilment at a given store.
   *
   * @param productId the ID of the product to assign
   * @param warehouseBusinessUnitCode the business unit code of the warehouse
   * @param storeId the ID of the store
   * @throws WebApplicationException 400 if any input is null or blank
   * @throws WebApplicationException 404 if product, warehouse, or store does not exist
   * @throws WebApplicationException 409 if association already exists or any cardinality constraint
   *     is violated
   */
  public void assign(Long productId, String warehouseBusinessUnitCode, Long storeId) {
    validateInputs(productId, warehouseBusinessUnitCode, storeId);
    LOGGER.debug(
        "Assigning product "
            + productId
            + " to warehouse "
            + warehouseBusinessUnitCode
            + " for store "
            + storeId);

    // Existence checks
    var product = productRepository.findById(productId);
    if (product == null) {
      LOGGER.error("Product not found: " + productId);
      throw new WebApplicationException("Product not found: " + productId, 404);
    }

    var warehouse = warehouseRepository.findByBusinessUnitCode(warehouseBusinessUnitCode);
    if (warehouse == null) {
      LOGGER.error("Warehouse not found: " + warehouseBusinessUnitCode);
      throw new WebApplicationException(
          "Warehouse not found: " + warehouseBusinessUnitCode, 404);
    }

    var store = storeRepository.findById(storeId);
    if (store == null) {
      LOGGER.error("Store not found: " + storeId);
      throw new WebApplicationException("Store not found: " + storeId, 404);
    }

    // Duplicate check
    var existing =
        associationRepository.findByKey(productId, warehouseBusinessUnitCode, storeId);
    if (existing != null) {
      LOGGER.error(
          "Association already exists: productId="
              + productId
              + ", warehouse="
              + warehouseBusinessUnitCode
              + ", storeId="
              + storeId);
      throw new WebApplicationException(
          "Association already exists for product "
              + productId
              + ", warehouse "
              + warehouseBusinessUnitCode
              + ", store "
              + storeId,
          409);
    }

    // Cardinality constraint 1: max 2 warehouses per product per store
    long warehousesForProductInStore =
        associationRepository.countWarehousesForProductInStore(productId, storeId);
    if (warehousesForProductInStore >= MAX_WAREHOUSES_PER_PRODUCT_PER_STORE) {
      LOGGER.error(
          "Product "
              + productId
              + " already has "
              + warehousesForProductInStore
              + " warehouses in store "
              + storeId
              + " (max "
              + MAX_WAREHOUSES_PER_PRODUCT_PER_STORE
              + ")");
      throw new WebApplicationException(
          "Product "
              + productId
              + " already reaches the maximum of "
              + MAX_WAREHOUSES_PER_PRODUCT_PER_STORE
              + " warehouses per store",
          409);
    }

    // Cardinality constraint 2: max 3 warehouses per store
    long warehousesForStore = associationRepository.countWarehousesForStore(storeId);
    if (warehousesForStore >= MAX_WAREHOUSES_PER_STORE) {
      LOGGER.error(
          "Store "
              + storeId
              + " already has "
              + warehousesForStore
              + " warehouses (max "
              + MAX_WAREHOUSES_PER_STORE
              + ")");
      throw new WebApplicationException(
          "Store "
              + storeId
              + " already reaches the maximum of "
              + MAX_WAREHOUSES_PER_STORE
              + " warehouses",
          409);
    }

    // Cardinality constraint 3: max 5 product types per warehouse
    long productTypesInWarehouse =
        associationRepository.countProductTypesInWarehouse(warehouseBusinessUnitCode);
    if (productTypesInWarehouse >= MAX_PRODUCT_TYPES_PER_WAREHOUSE) {
      LOGGER.error(
          "Warehouse "
              + warehouseBusinessUnitCode
              + " already stores "
              + productTypesInWarehouse
              + " product types (max "
              + MAX_PRODUCT_TYPES_PER_WAREHOUSE
              + ")");
      throw new WebApplicationException(
          "Warehouse "
              + warehouseBusinessUnitCode
              + " already reaches the maximum of "
              + MAX_PRODUCT_TYPES_PER_WAREHOUSE
              + " product types",
          409);
    }

    var association =
        new DbProductWarehouseStoreAssociation(
            productId, warehouseBusinessUnitCode, storeId, LocalDateTime.now());
    associationRepository.create(association);
    LOGGER.info(
        "Product "
            + productId
            + " assigned to warehouse "
            + warehouseBusinessUnitCode
            + " for store "
            + storeId);
  }

  /**
   * Validates that all required input parameters are present and non-blank.
   *
   * @throws WebApplicationException 400 if any parameter is null or blank
   */
  private void validateInputs(Long productId, String warehouseBusinessUnitCode, Long storeId) {
    if (productId == null) {
      LOGGER.error("Product ID cannot be null");
      throw new WebApplicationException("Product ID is required", 400);
    }
    if (warehouseBusinessUnitCode == null || warehouseBusinessUnitCode.isBlank()) {
      LOGGER.error("Warehouse business unit code cannot be null or blank");
      throw new WebApplicationException("Warehouse business unit code is required", 400);
    }
    if (storeId == null) {
      LOGGER.error("Store ID cannot be null");
      throw new WebApplicationException("Store ID is required", 400);
    }
  }
}
