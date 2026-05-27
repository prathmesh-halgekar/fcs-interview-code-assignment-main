package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;

import java.time.LocalDateTime;
import org.jboss.logging.Logger;

/**
 * Use case for archiving warehouses via soft delete.
 *
 * Sets the {@code archivedAt} timestamp on the warehouse, marking it as inactive without
 * physically removing the record. This preserves the audit trail and allows data recovery.
 *
 * This operation is idempotent: archiving an already-archived warehouse is a no-op.
 */
@ApplicationScoped
public class ArchiveWarehouseUseCase implements ArchiveWarehouseOperation {

  private static final Logger LOGGER = Logger.getLogger(ArchiveWarehouseUseCase.class.getName());

  private final WarehouseStore warehouseStore;

  public ArchiveWarehouseUseCase(WarehouseStore warehouseStore) {
    this.warehouseStore = warehouseStore;
  }

  /**
   * Archives the given warehouse by setting its {@code archivedAt} timestamp to now.
   *
   * If the warehouse is already archived, the operation is skipped (idempotent).
   *
   * @param warehouse the warehouse to archive; must not be null
   * @throws WebApplicationException 400 if warehouse is null
   */
  @Override
  public void archive(Warehouse warehouse) {
    if (warehouse == null) {
      LOGGER.error("Warehouse object cannot be null");
      throw new WebApplicationException("Warehouse object cannot be null", 400);
    }

    if (warehouse.businessUnitCode == null || warehouse.businessUnitCode.isBlank()) {
      LOGGER.error("Warehouse business unit code cannot be null or blank");
      throw new WebApplicationException("Business unit code is required", 400);
    }

    if (warehouse.archivedAt != null) {
      LOGGER.warn(
          "Warehouse already archived, skipping: "
              + warehouse.businessUnitCode
              + " (archived at "
              + warehouse.archivedAt
              + ")");
      return;
    }

    warehouse.archivedAt = LocalDateTime.now();
    LOGGER.debug("Setting archivedAt for warehouse: " + warehouse.businessUnitCode);

    warehouseStore.update(warehouse);
    LOGGER.info(
        "Warehouse archived: "
            + warehouse.businessUnitCode
            + " at "
            + warehouse.archivedAt);
  }
}
