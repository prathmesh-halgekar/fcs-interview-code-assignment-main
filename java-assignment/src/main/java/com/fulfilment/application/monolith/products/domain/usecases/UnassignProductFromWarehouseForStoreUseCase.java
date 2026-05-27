package com.fulfilment.application.monolith.products.domain.usecases;

import com.fulfilment.application.monolith.products.adapters.database.ProductWarehouseStoreRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.logging.Logger;

/**
 * Use case for removing a Product-Warehouse-Store association (unassignment).
 *
 * <p>Verifies the association exists before deleting it (hard delete; no soft-delete semantics
 * apply to associations).
 */
@ApplicationScoped
public class UnassignProductFromWarehouseForStoreUseCase {

  private static final Logger LOGGER =
      Logger.getLogger(UnassignProductFromWarehouseForStoreUseCase.class.getName());

  @Inject ProductWarehouseStoreRepository associationRepository;

  /**
   * Removes the association linking the given product, warehouse, and store.
   *
   * @param productId the ID of the product
   * @param warehouseBusinessUnitCode the business unit code of the warehouse
   * @param storeId the ID of the store
   * @throws WebApplicationException 400 if any input is null or blank
   * @throws WebApplicationException 404 if the association does not exist
   */
  public void unassign(Long productId, String warehouseBusinessUnitCode, Long storeId) {
    validateInputs(productId, warehouseBusinessUnitCode, storeId);
    LOGGER.debug(
        "Unassigning product "
            + productId
            + " from warehouse "
            + warehouseBusinessUnitCode
            + " for store "
            + storeId);

    var existing = associationRepository.findByKey(productId, warehouseBusinessUnitCode, storeId);
    if (existing == null) {
      LOGGER.error(
          "Association not found: productId="
              + productId
              + ", warehouse="
              + warehouseBusinessUnitCode
              + ", storeId="
              + storeId);
      throw new WebApplicationException(
          "Association not found for product "
              + productId
              + ", warehouse "
              + warehouseBusinessUnitCode
              + ", store "
              + storeId,
          404);
    }

    associationRepository.remove(productId, warehouseBusinessUnitCode, storeId);
    LOGGER.info(
        "Product "
            + productId
            + " unassigned from warehouse "
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
