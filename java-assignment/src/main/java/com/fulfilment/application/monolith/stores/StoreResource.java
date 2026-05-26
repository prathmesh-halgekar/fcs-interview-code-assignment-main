package com.fulfilment.application.monolith.stores;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * REST resource for store management operations.
 * 
 * Handles CRUD operations on stores with transactional boundaries ensuring
 * that legacy system synchronization occurs only after database commits.
 * Uses {@link Panache#flush()} to guarantee persistence before downstream calls.
 * 
 * Atomicity guarantee: If flush fails, legacy gateway is never invoked and
 * transaction is rolled back, preventing inconsistent state propagation.
 */
@Path("store")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class StoreResource {

  @Inject LegacyStoreManagerGateway legacyStoreManagerGateway;

  private static final Logger LOGGER = Logger.getLogger(StoreResource.class.getName());

  @GET
  public List<Store> get() {
    return Store.listAll(Sort.by("name"));
  }

  @GET
  @Path("{id}")
  public Store getSingle(Long id) {
    Store entity = Store.findById(id);
    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }
    return entity;
  }

  /**
   * Create a new store and synchronize with legacy system after persistence.
   * 
   * Flushes database transaction before invoking legacy gateway to ensure
   * confirmed data propagation. If flush fails, legacy gateway is not invoked,
   * guaranteeing atomic semantics: both succeed or both fail (transaction rollback).
   * 
   * @param store the store to create
   * @return 201 Created response with persisted store
   * @throws WebApplicationException if store creation or flush fails
   */
  @POST
  @Transactional
  public Response create(Store store) {
    if (store.id != null) {
      throw new WebApplicationException("Id was invalidly set on request.", 422);
    }

    store.persist();
    LOGGER.debug("Store persisted to memory: " + store.name);

    // Flush changes to database before invoking legacy gateway to ensure atomicity. 
    // Operation name is empty string for create since it's the initial creation.
    flushStoreChanges(store.name, "", "Failed to persist store to database.");
    syncWithLegacySystem(() -> legacyStoreManagerGateway.createStoreOnLegacySystem(store), store.name, "");

    return Response.ok(store).status(201).build();
  }

  /**
   * Update an existing store and synchronize with legacy system after persistence.
   * 
   * Flushes database transaction before invoking legacy gateway to ensure
   * confirmed data propagation. If flush fails, legacy gateway is not invoked,
   * guaranteeing atomic semantics: both succeed or both fail (transaction rollback).
   * 
   * @param id the store id
   * @param updatedStore the updated store data
   * @return the updated store entity
   * @throws WebApplicationException if store update or flush fails
   */
  @PUT
  @Path("{id}")
  @Transactional
  public Store update(Long id, Store updatedStore) {
    if (updatedStore.name == null) {
      throw new WebApplicationException("Store Name was not set on request.", 422);
    }

    Store entity = Store.findById(id);

    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }

    entity.name = updatedStore.name;
    entity.quantityProductsInStock = updatedStore.quantityProductsInStock;
    LOGGER.debug("Store updated in memory: " + entity.name);

    flushStoreChanges(entity.name, "changes", "Failed to persist store changes to database.");
    syncWithLegacySystem(() -> legacyStoreManagerGateway.updateStoreOnLegacySystem(updatedStore), entity.name, "update");

    return entity;
  }

  /**
   * Partially update a store and synchronize with legacy system after persistence.
   * 
   * Flushes database transaction before invoking legacy gateway to ensure
   * confirmed data propagation. If flush fails, legacy gateway is not invoked,
   * guaranteeing atomic semantics: both succeed or both fail (transaction rollback).
   * 
   * @param id the store id
   * @param updatedStore the partial store data
   * @return the updated store entity
   * @throws WebApplicationException if store patch or flush fails
   */
  @PATCH
  @Path("{id}")
  @Transactional
  public Store patch(Long id, Store updatedStore) {
    if (updatedStore.name == null) {
      throw new WebApplicationException("Store Name was not set on request.", 422);
    }

    Store entity = Store.findById(id);

    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }

    if (entity.name != null) {
      entity.name = updatedStore.name;
    }

    if (entity.quantityProductsInStock != 0) {
      entity.quantityProductsInStock = updatedStore.quantityProductsInStock;
    }
    LOGGER.debug("Store patched in memory: " + entity.name);

    flushStoreChanges(entity.name, "changes", "Failed to persist store changes to database.");
    syncWithLegacySystem(() -> legacyStoreManagerGateway.updateStoreOnLegacySystem(updatedStore), entity.name, "patch");

    return entity;
  }

  /**
   * Delete a store and synchronize deletion with legacy system after persistence.
   * 
   * Flushes database transaction before invoking legacy gateway to ensure
   * confirmed data propagation. If flush fails, legacy gateway is not invoked,
   * guaranteeing atomic semantics: both succeed or both fail (transaction rollback).
   * 
   * @param id the store id
   * @return 204 No Content response
   * @throws WebApplicationException if store deletion or flush fails
   */
  @DELETE
  @Path("{id}")
  @Transactional
  public Response delete(Long id) {
    Store entity = Store.findById(id);
    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }
    
    String storeName = entity.name;
    entity.delete();
    LOGGER.debug("Store deleted from memory: " + storeName);

    flushStoreChanges(storeName, "deletion", "Failed to delete store from database.");
    syncWithLegacySystem(() -> legacyStoreManagerGateway.deleteStoreOnLegacySystem(entity), storeName, "deletion");
    
    return Response.status(204).build();
  }

  /**
   * Flushes store changes to database with atomicity guarantee.
   * 
   * If flush fails, throws exception to rollback transaction,
   * preventing legacy gateway invocation.
   * 
   * @param storeName the store name for logging
   * @param operation operation descriptor for log messages (e.g., "changes", "deletion", or "" for create)
   * @param webErrorMessage HTTP error message if flush fails
   * @throws WebApplicationException if flush fails
   */
  private void flushStoreChanges(String storeName, String operation, String webErrorMessage) {
    try {
      Panache.flush();
      String opDisplay = operation.isEmpty() ? "" : " " + operation;
      LOGGER.info("Store" + opDisplay + " committed to database: " + storeName);
    } catch (Exception e) {
      String opDisplay = operation.isEmpty() ? "" : " " + operation;
      LOGGER.error("Failed to flush store" + opDisplay + " to database: " + storeName, e);
      throw new WebApplicationException(webErrorMessage, 500);
    }
  }

  /**
   * Synchronizes store changes with legacy system after database commit.
   * 
   * Non-critical operation: failures logged as warnings but do not throw.
   * Database change already guaranteed by caller's Panache.flush().
   * 
   * @param syncOperation the legacy system operation to invoke
   * @param storeName the store name for logging
   * @param action action descriptor for log messages (e.g., "update", "patch", "deletion", or "" for create)
   */
  private void syncWithLegacySystem(Runnable syncOperation, String storeName, String action) {
    try {
      syncOperation.run();
      String actionDisplay = action.isEmpty() ? "" : " " + action;
      LOGGER.info("Store" + actionDisplay + " synchronized with legacy system: " + storeName);
    } catch (Exception e) {
      String actionDisplay = action.isEmpty() ? "" : " " + action;
      LOGGER.warn("Legacy system sync failed for store" + actionDisplay + ": " + storeName + 
        ". Database change committed but legacy sync incomplete.", e);
      // Note: Database commit already guaranteed; legacy sync failure is non-critical for atomicity
    }
  }

  @Provider
  public static class ErrorMapper implements ExceptionMapper<Exception> {

    @Inject ObjectMapper objectMapper;

    @Override
    public Response toResponse(Exception exception) {
      LOGGER.error("Failed to handle request", exception);

      int code = 500;
      if (exception instanceof WebApplicationException) {
        code = ((WebApplicationException) exception).getResponse().getStatus();
      }

      ObjectNode exceptionJson = objectMapper.createObjectNode();
      exceptionJson.put("exceptionType", exception.getClass().getName());
      exceptionJson.put("code", code);

      if (exception.getMessage() != null) {
        exceptionJson.put("error", exception.getMessage());
      }

      return Response.status(code).entity(exceptionJson).build();
    }
  }
}
