package com.fulfilment.application.monolith.products.adapters.restapi;

import com.fulfilment.application.monolith.products.adapters.database.DbProductWarehouseStoreAssociation;
import com.fulfilment.application.monolith.products.adapters.database.ProductWarehouseStoreRepository;
import com.fulfilment.application.monolith.products.domain.usecases.AssignProductToWarehouseForStoreUseCase;
import com.fulfilment.application.monolith.products.domain.usecases.UnassignProductFromWarehouseForStoreUseCase;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * REST resource for managing Product-Warehouse-Store fulfilment associations.
 *
 * <p>Exposes endpoints to assign and unassign warehouses as fulfilment units for products in stores,
 * and to query the associations from different perspectives (by store, by warehouse, by product+store).
 *
 * <p>All write operations are transactional. Constraint violations return HTTP 409; missing entities
 * return HTTP 404; invalid input returns HTTP 400.
 */
@Path("")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class ProductWarehouseStoreResource {

  private static final Logger LOGGER =
      Logger.getLogger(ProductWarehouseStoreResource.class.getName());

  @Inject AssignProductToWarehouseForStoreUseCase assignUseCase;
  @Inject UnassignProductFromWarehouseForStoreUseCase unassignUseCase;
  @Inject ProductWarehouseStoreRepository associationRepository;

  /**
   * Assigns a product to a warehouse as a fulfilment unit for a given store.
   *
   * @param productId the ID of the product
   * @param warehouseCode the business unit code of the warehouse
   * @param storeId the ID of the store
   * @return HTTP 201 Created on success
   * @throws jakarta.ws.rs.WebApplicationException 400/404/409 on validation or constraint failure
   */
  @POST
  @Transactional
  @Path("/product/{productId}/warehouse/{warehouseCode}/store/{storeId}")
  public Response assign(
      @PathParam("productId") Long productId,
      @PathParam("warehouseCode") String warehouseCode,
      @PathParam("storeId") Long storeId) {
    LOGGER.debug(
        "POST assign: productId=" + productId + ", warehouse=" + warehouseCode + ", storeId=" + storeId);
    assignUseCase.assign(productId, warehouseCode, storeId);
    LOGGER.info("Association created: productId=" + productId + ", warehouse=" + warehouseCode + ", storeId=" + storeId);
    return Response.status(Response.Status.CREATED).build();
  }

  /**
   * Removes the association between a product, warehouse, and store.
   *
   * @param productId the ID of the product
   * @param warehouseCode the business unit code of the warehouse
   * @param storeId the ID of the store
   * @return HTTP 204 No Content on success
   * @throws jakarta.ws.rs.WebApplicationException 400/404 on validation failure or if not found
   */
  @DELETE
  @Transactional
  @Path("/product/{productId}/warehouse/{warehouseCode}/store/{storeId}")
  public Response unassign(
      @PathParam("productId") Long productId,
      @PathParam("warehouseCode") String warehouseCode,
      @PathParam("storeId") Long storeId) {
    LOGGER.debug(
        "DELETE unassign: productId=" + productId + ", warehouse=" + warehouseCode + ", storeId=" + storeId);
    unassignUseCase.unassign(productId, warehouseCode, storeId);
    LOGGER.info("Association removed: productId=" + productId + ", warehouse=" + warehouseCode + ", storeId=" + storeId);
    return Response.noContent().build();
  }

  /**
   * Lists all warehouses assigned to a given store.
   *
   * @param storeId the ID of the store
   * @return HTTP 200 with list of associations
   */
  @GET
  @Path("/store/{storeId}/warehouses")
  public List<AssociationResponse> listWarehousesForStore(@PathParam("storeId") Long storeId) {
    LOGGER.debug("GET warehouses for store: " + storeId);
    return associationRepository.findByStoreId(storeId).stream()
        .map(this::toResponse)
        .toList();
  }

  /**
   * Lists all product types stored in a given warehouse.
   *
   * @param warehouseCode the business unit code of the warehouse
   * @return HTTP 200 with list of associations
   */
  @GET
  @Path("/warehouse/{warehouseCode}/products")
  public List<AssociationResponse> listProductsInWarehouse(
      @PathParam("warehouseCode") String warehouseCode) {
    LOGGER.debug("GET products in warehouse: " + warehouseCode);
    return associationRepository.findByWarehouseCode(warehouseCode).stream()
        .map(this::toResponse)
        .toList();
  }

  /**
   * Lists all warehouses fulfilling a specific product for a given store.
   *
   * @param productId the ID of the product
   * @param storeId the ID of the store
   * @return HTTP 200 with list of associations
   */
  @GET
  @Path("/product/{productId}/store/{storeId}/warehouses")
  public List<AssociationResponse> listWarehousesForProductInStore(
      @PathParam("productId") Long productId, @PathParam("storeId") Long storeId) {
    LOGGER.debug("GET warehouses for product " + productId + " in store " + storeId);
    return associationRepository.findByProductIdAndStoreId(productId, storeId).stream()
        .map(this::toResponse)
        .toList();
  }

  private AssociationResponse toResponse(DbProductWarehouseStoreAssociation association) {
    return new AssociationResponse(
        association.productId,
        association.warehouseBusinessUnitCode,
        association.storeId,
        association.createdAt != null ? association.createdAt.toString() : null);
  }

  /**
   * Response bean representing a Product-Warehouse-Store association.
   */
  public record AssociationResponse(
      Long productId, String warehouseBusinessUnitCode, Long storeId, String createdAt) {}
}
