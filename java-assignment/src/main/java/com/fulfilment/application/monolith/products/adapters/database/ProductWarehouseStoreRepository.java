package com.fulfilment.application.monolith.products.adapters.database;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Persistence adapter for Product-Warehouse-Store associations.
 *
 * <p>Provides CRUD operations and count queries used to enforce the three cardinality constraints:
 *
 * <ul>
 *   <li>Max 2 warehouses per product per store.
 *   <li>Max 3 warehouses per store.
 *   <li>Max 5 product types per warehouse.
 * </ul>
 */
@ApplicationScoped
public class ProductWarehouseStoreRepository
    implements PanacheRepository<DbProductWarehouseStoreAssociation> {

  private static final Logger LOGGER =
      Logger.getLogger(ProductWarehouseStoreRepository.class.getName());

  private static final String FIND_BY_KEY_QUERY =
      "productId = ?1 AND warehouseBusinessUnitCode = ?2 AND storeId = ?3";
  private static final String COUNT_WAREHOUSES_FOR_PRODUCT_IN_STORE_QUERY =
      "SELECT COUNT(DISTINCT a.warehouseBusinessUnitCode) FROM DbProductWarehouseStoreAssociation a"
          + " WHERE a.productId = ?1 AND a.storeId = ?2";
  private static final String COUNT_WAREHOUSES_FOR_STORE_QUERY =
      "SELECT COUNT(DISTINCT a.warehouseBusinessUnitCode) FROM DbProductWarehouseStoreAssociation a"
          + " WHERE a.storeId = ?1";
  private static final String COUNT_PRODUCT_TYPES_IN_WAREHOUSE_QUERY =
      "SELECT COUNT(DISTINCT a.productId) FROM DbProductWarehouseStoreAssociation a"
          + " WHERE a.warehouseBusinessUnitCode = ?1";

  /**
   * Returns the association matching the given composite key, or {@code null} if not found.
   *
   * @param productId the product identifier
   * @param warehouseBusinessUnitCode the warehouse business unit code
   * @param storeId the store identifier
   * @return the matching association, or null
   */
  public DbProductWarehouseStoreAssociation findByKey(
      Long productId, String warehouseBusinessUnitCode, Long storeId) {
    LOGGER.debug(
        "Looking up association: productId="
            + productId
            + ", warehouse="
            + warehouseBusinessUnitCode
            + ", storeId="
            + storeId);
    return find(FIND_BY_KEY_QUERY, productId, warehouseBusinessUnitCode, storeId).firstResult();
  }

  /**
   * Persists a new association.
   *
   * @param association the association to create
   */
  public void create(DbProductWarehouseStoreAssociation association) {
    persist(association);
    LOGGER.info(
        "Association created: productId="
            + association.productId
            + ", warehouse="
            + association.warehouseBusinessUnitCode
            + ", storeId="
            + association.storeId);
  }

  /**
   * Hard-deletes the association matching the given composite key.
   *
   * @param productId the product identifier
   * @param warehouseBusinessUnitCode the warehouse business unit code
   * @param storeId the store identifier
   */
  public void remove(Long productId, String warehouseBusinessUnitCode, Long storeId) {
    delete(FIND_BY_KEY_QUERY, productId, warehouseBusinessUnitCode, storeId);
    LOGGER.info(
        "Association removed: productId="
            + productId
            + ", warehouse="
            + warehouseBusinessUnitCode
            + ", storeId="
            + storeId);
  }

  /**
   * Returns all associations for a given store.
   *
   * @param storeId the store identifier
   * @return list of associations for the store
   */
  public List<DbProductWarehouseStoreAssociation> findByStoreId(Long storeId) {
    return find("storeId", storeId).list();
  }

  /**
   * Returns all associations for a given warehouse.
   *
   * @param warehouseBusinessUnitCode the warehouse business unit code
   * @return list of associations for the warehouse
   */
  public List<DbProductWarehouseStoreAssociation> findByWarehouseCode(
      String warehouseBusinessUnitCode) {
    return find("warehouseBusinessUnitCode", warehouseBusinessUnitCode).list();
  }

  /**
   * Returns all associations for a given product in a given store.
   *
   * @param productId the product identifier
   * @param storeId the store identifier
   * @return list of associations for the product+store combination
   */
  public List<DbProductWarehouseStoreAssociation> findByProductIdAndStoreId(
      Long productId, Long storeId) {
    return find("productId = ?1 AND storeId = ?2", productId, storeId).list();
  }

  /**
   * Counts the number of distinct warehouses fulfilling the given product in the given store.
   * Used to enforce: max 2 warehouses per product per store.
   *
   * @param productId the product identifier
   * @param storeId the store identifier
   * @return count of distinct warehouse codes
   */
  public long countWarehousesForProductInStore(Long productId, Long storeId) {
    return (long) getEntityManager()
        .createQuery(COUNT_WAREHOUSES_FOR_PRODUCT_IN_STORE_QUERY)
        .setParameter(1, productId)
        .setParameter(2, storeId)
        .getSingleResult();
  }

  /**
   * Counts the number of distinct warehouses serving a given store.
   * Used to enforce: max 3 warehouses per store.
   *
   * @param storeId the store identifier
   * @return count of distinct warehouse codes
   */
  public long countWarehousesForStore(Long storeId) {
    return (long) getEntityManager()
        .createQuery(COUNT_WAREHOUSES_FOR_STORE_QUERY)
        .setParameter(1, storeId)
        .getSingleResult();
  }

  /**
   * Counts the number of distinct product types stored in a given warehouse.
   * Used to enforce: max 5 product types per warehouse.
   *
   * @param warehouseBusinessUnitCode the warehouse business unit code
   * @return count of distinct product IDs
   */
  public long countProductTypesInWarehouse(String warehouseBusinessUnitCode) {
    return (long) getEntityManager()
        .createQuery(COUNT_PRODUCT_TYPES_IN_WAREHOUSE_QUERY)
        .setParameter(1, warehouseBusinessUnitCode)
        .getSingleResult();
  }
}
