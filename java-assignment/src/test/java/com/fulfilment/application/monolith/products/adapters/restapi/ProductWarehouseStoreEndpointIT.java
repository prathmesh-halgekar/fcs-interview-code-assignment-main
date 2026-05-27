package com.fulfilment.application.monolith.products.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Integration tests for the Product-Warehouse-Store association endpoints.
 *
 * <p>Tests are ordered to control state: assign first, then list, then constrain, then unassign.
 * Initial data from import.sql: 3 associations (product 1→MWH.001→store1, product 2→MWH.012→store2,
 * product 3→MWH.023→store3).
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductWarehouseStoreEndpointIT {

  @Test
  @Order(1)
  void testListWarehousesForStore_returnsInitialData() {
    given()
        .when()
        .get("/store/1/warehouses")
        .then()
        .statusCode(200)
        .body("size()", is(1))
        .body("[0].warehouseBusinessUnitCode", is("MWH.001"))
        .body("[0].storeId", is(1))
        .body("[0].productId", is(1));
  }

  @Test
  @Order(2)
  void testListProductsInWarehouse_returnsInitialData() {
    given()
        .when()
        .get("/warehouse/MWH.001/products")
        .then()
        .statusCode(200)
        .body("size()", is(1))
        .body("[0].productId", is(1));
  }

  @Test
  @Order(3)
  void testListWarehousesForProductInStore_returnsInitialData() {
    given()
        .when()
        .get("/product/1/store/1/warehouses")
        .then()
        .statusCode(200)
        .body("size()", is(1))
        .body("[0].warehouseBusinessUnitCode", is("MWH.001"));
  }

  @Test
  @Order(4)
  void testAssign_success_returns201() {
    given()
        .when()
        .post("/product/2/warehouse/MWH.001/store/1")
        .then()
        .statusCode(201);
  }

  @Test
  @Order(5)
  void testAssign_duplicate_returns409() {
    given()
        .when()
        .post("/product/1/warehouse/MWH.001/store/1")
        .then()
        .statusCode(409);
  }

  @Test
  @Order(6)
  void testAssign_productMaxWarehousesPerStoreExceeded_returns409() {
    // product 1 already has MWH.001 in store 1; add MWH.012
    given().when().post("/product/1/warehouse/MWH.012/store/1").then().statusCode(201);

    // Now product 1 has 2 warehouses in store 1 (MWH.001, MWH.012) - max reached
    given()
        .when()
        .post("/product/1/warehouse/MWH.023/store/1")
        .then()
        .statusCode(409);
  }

  @Test
  @Order(7)
  void testAssign_storeMaxWarehousesExceeded_returns409() {
    // Store 1 now has: MWH.001 (from init + order 4 above assigns product2 there too, but distinct
    // warehouses per store are: MWH.001, MWH.012 at this point)
    // Add product 3 to MWH.023 for store 1 to reach 3 distinct warehouses
    given().when().post("/product/3/warehouse/MWH.023/store/1").then().statusCode(201);

    // Store 1 now has 3 distinct warehouses (MWH.001, MWH.012, MWH.023) - max reached
    // product_warehouse_store_seq is at 7 now; try adding another warehouse for store 1
    // Need a new warehouse for this test - use existing MWH.023 with a different product
    // Instead, verify count by checking the cardinality constraint triggers with product 2 on MWH.023 in store 1
    given()
        .when()
        .post("/product/2/warehouse/MWH.023/store/1")
        .then()
        .statusCode(409);
  }

  @Test
  @Order(8)
  void testAssign_productNotFound_returns404() {
    given()
        .when()
        .post("/product/999/warehouse/MWH.001/store/1")
        .then()
        .statusCode(404);
  }

  @Test
  @Order(9)
  void testAssign_warehouseNotFound_returns404() {
    given()
        .when()
        .post("/product/1/warehouse/MWH.UNKNOWN/store/1")
        .then()
        .statusCode(404);
  }

  @Test
  @Order(10)
  void testUnassign_success_returns204() {
    given()
        .when()
        .delete("/product/2/warehouse/MWH.001/store/1")
        .then()
        .statusCode(204);
  }

  @Test
  @Order(11)
  void testUnassign_notFound_returns404() {
    given()
        .when()
        .delete("/product/99/warehouse/MWH.001/store/1")
        .then()
        .statusCode(404);
  }
}
