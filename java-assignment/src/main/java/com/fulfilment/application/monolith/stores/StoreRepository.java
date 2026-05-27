package com.fulfilment.application.monolith.stores;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for Store lookups. Provides an injectable, mockable alternative to the
 * PanacheEntity static methods on {@link Store}, enabling use case unit testing.
 */
@ApplicationScoped
public class StoreRepository implements PanacheRepository<Store> {}
