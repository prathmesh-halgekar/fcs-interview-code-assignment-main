package com.fulfilment.application.monolith.products.adapters.database;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * JPA entity representing a Product-Warehouse-Store association.
 *
 * <p>Records that a given product can be fulfilled from a specific warehouse for a specific store.
 * Three cardinality constraints are enforced at the application layer:
 *
 * <ul>
 *   <li>A product can be fulfilled by at most 2 warehouses per store.
 *   <li>A store can be served by at most 3 warehouses.
 *   <li>A warehouse can store at most 5 distinct product types.
 * </ul>
 *
 * <p>No soft delete: associations are hard-deleted when unassigned.
 */
@Entity
@Table(
    name = "product_warehouse_store",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_product_warehouse_store",
          columnNames = {"product_id", "warehouse_business_unit_code", "store_id"})
    })
public class DbProductWarehouseStoreAssociation {

  @Id @GeneratedValue public Long id;

  @Column(name = "product_id", nullable = false)
  public Long productId;

  @Column(name = "warehouse_business_unit_code", nullable = false)
  public String warehouseBusinessUnitCode;

  @Column(name = "store_id", nullable = false)
  public Long storeId;

  @Column(name = "created_at", nullable = false)
  public LocalDateTime createdAt;

  public DbProductWarehouseStoreAssociation() {}

  public DbProductWarehouseStoreAssociation(
      Long productId, String warehouseBusinessUnitCode, Long storeId, LocalDateTime createdAt) {
    this.productId = productId;
    this.warehouseBusinessUnitCode = warehouseBusinessUnitCode;
    this.storeId = storeId;
    this.createdAt = createdAt;
  }
}
