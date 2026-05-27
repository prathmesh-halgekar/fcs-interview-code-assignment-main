package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import java.util.List;

import org.jboss.logging.Logger;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.usecases.ArchiveWarehouseUseCase;
import com.fulfilment.application.monolith.warehouses.domain.usecases.CreateWarehouseUseCase;
import com.fulfilment.application.monolith.warehouses.domain.usecases.ReplaceWarehouseUseCase;
import com.warehouse.api.WarehouseResource;
import com.warehouse.api.beans.Warehouse;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.WebApplicationException;

/**
 * REST adapter implementing the warehouse API generated from the OpenAPI specification.
 *
 * Delegates all business logic and validation to use cases; this layer is responsible only for
 * HTTP mapping: translating API beans to domain models, invoking use cases, and mapping results
 * back to API response beans.
 *
 * Soft delete semantics: archived warehouses are excluded from list and get-by-ID responses.
 */
@RequestScoped
public class WarehouseResourceImpl implements WarehouseResource {

  private static final Logger LOGGER = Logger.getLogger(WarehouseResourceImpl.class.getName());

  @Inject private WarehouseRepository warehouseRepository;
  @Inject private CreateWarehouseUseCase createWarehouseUseCase;
  @Inject private ArchiveWarehouseUseCase archiveWarehouseUseCase;
  @Inject private ReplaceWarehouseUseCase replaceWarehouseUseCase;

  /**
   * Lists all active (non-archived) warehouse units.
   *
   * @return list of active warehouses; empty list if none exist
   */
  @Override
  public List<Warehouse> listAllWarehousesUnits() {
    LOGGER.debug("Listing all active warehouse units");
    return warehouseRepository.getAll().stream().map(this::toWarehouseResponse).toList();
  }

  /**
   * Creates a new warehouse unit after full validation by the use case.
   *
   * @param data the warehouse data to create; must pass all business rule validations
   * @return the created warehouse with HTTP 201
   * @throws WebApplicationException 400/404/409 if validation fails
   */
  @Override
  @Transactional
  public Warehouse createANewWarehouseUnit(@NotNull Warehouse data) {
    LOGGER.debug("Creating new warehouse unit: " + data.getBusinessUnitCode());
    var domainWarehouse = toDomainModel(data);
    createWarehouseUseCase.create(domainWarehouse);
    LOGGER.info("Warehouse unit created: " + data.getBusinessUnitCode());
    return toWarehouseResponse(domainWarehouse);
  }

  /**
   * Retrieves an active warehouse by its business unit code. Archived warehouses are not returned.
   *
   * @param id the business unit code of the warehouse to retrieve
   * @return the matching active warehouse
   * @throws WebApplicationException 404 if warehouse does not exist or is archived
   */
  @Override
  public Warehouse getAWarehouseUnitByID(String id) {
    LOGGER.debug("Fetching warehouse by BUC: " + id);
    var warehouse = warehouseRepository.findByBusinessUnitCode(id);
    if (warehouse == null) {
      LOGGER.warn("Warehouse not found: " + id);
      throw new WebApplicationException("Warehouse not found: " + id, 404);
    }
    LOGGER.debug("Warehouse found: " + id);
    return toWarehouseResponse(warehouse);
  }

  /**
   * Archives a warehouse by its business unit code (soft delete). The record remains in the
   * database with {@code archivedAt} set; subsequent GET calls will return 404.
   *
   * @param id the business unit code of the warehouse to archive
   * @throws WebApplicationException 404 if warehouse does not exist or is already archived
   */
  @Override
  @Transactional
  public void archiveAWarehouseUnitByID(String id) {
    LOGGER.debug("Archiving warehouse: " + id);
    var warehouse = warehouseRepository.findByBusinessUnitCode(id);
    if (warehouse == null) {
      LOGGER.warn("Warehouse not found for archiving: " + id);
      throw new WebApplicationException("Warehouse not found: " + id, 404);
    }
    archiveWarehouseUseCase.archive(warehouse);
    LOGGER.info("Warehouse archived: " + id);
  }

  /**
   * Replaces the current active warehouse identified by the given business unit code. The old
   * warehouse is archived and the new warehouse is created atomically within the same transaction.
   *
   * @param businessUnitCode the BUC of the warehouse to replace
   * @param data the new warehouse data
   * @return the newly created replacement warehouse
   * @throws WebApplicationException 400/404/409 if validation fails
   */
  @Override
  @Transactional
  public Warehouse replaceTheCurrentActiveWarehouse(
      String businessUnitCode, @NotNull Warehouse data) {
    LOGGER.debug("Replacing warehouse: " + businessUnitCode);
    var domainWarehouse = toDomainModel(data);
    // Set the BUC from the URL path so the use case can locate the existing warehouse
    domainWarehouse.businessUnitCode = businessUnitCode;
    replaceWarehouseUseCase.replace(domainWarehouse);
    LOGGER.info("Warehouse replaced: " + businessUnitCode);
    return toWarehouseResponse(domainWarehouse);
  }

  /**
   * Converts a domain Warehouse model to the API response bean.
   *
   * @param warehouse the domain model to convert
   * @return the corresponding API bean
   */
  private Warehouse toWarehouseResponse(
      com.fulfilment.application.monolith.warehouses.domain.models.Warehouse warehouse) {
    var response = new Warehouse();
    response.setBusinessUnitCode(warehouse.businessUnitCode);
    response.setLocation(warehouse.location);
    response.setCapacity(warehouse.capacity);
    response.setStock(warehouse.stock);
    return response;
  }

  /**
   * Converts an API Warehouse bean to the domain model.
   *
   * @param data the API bean to convert
   * @return the corresponding domain model
   */
  private com.fulfilment.application.monolith.warehouses.domain.models.Warehouse toDomainModel(
      Warehouse data) {
    var warehouse =
        new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();
    warehouse.businessUnitCode = data.getBusinessUnitCode();
    warehouse.location = data.getLocation();
    warehouse.capacity = data.getCapacity();
    warehouse.stock = data.getStock();
    return warehouse;
  }
}
