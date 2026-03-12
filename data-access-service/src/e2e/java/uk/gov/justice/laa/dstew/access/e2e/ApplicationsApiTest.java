package uk.gov.justice.laa.dstew.access.e2e;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ApplicationsApiTest extends BaseApiTest {

  private static String seededCaseworkerId;

  @BeforeAll
  static void seedData() throws Exception {
    seeder.seedApplicationWithIndividual();
    seededCaseworkerId = seeder.insertCaseworker("e2e-applications-caseworker").toString();
  }

  @Test
  void getApplications_shouldReturn200AndMatchSpec() {
    RestAssured.given()
        .spec(spec)
    .when()
        .get("/applications")
    .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("paging.totalRecords", greaterThanOrEqualTo(1));
  }

  @Test
  void getApplications_withStatusFilter_shouldReturn200() {
    RestAssured.given()
        .spec(spec)
        .queryParam("status", "APPLICATION_IN_PROGRESS")
    .when()
        .get("/applications")
    .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("paging.totalRecords", greaterThanOrEqualTo(1))
        .body("applications.status", everyItem(equalTo("APPLICATION_IN_PROGRESS")));
  }

  @Test
  void getApplications_withPagination_shouldReturn200() {
    RestAssured.given()
        .spec(spec)
        .queryParam("page", 1)
        .queryParam("pageSize", 2)
    .when()
        .get("/applications")
    .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("paging.pageSize", equalTo(2));
  }

  @Test
  void getApplications_withSorting_shouldReturn200() {
    RestAssured.given()
        .spec(spec)
        .queryParam("sortBy", "SUBMITTED_DATE")
        .queryParam("orderBy", "DESC")
    .when()
        .get("/applications")
    .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("paging.totalRecords", greaterThanOrEqualTo(1));
  }

  @Test
  void getApplications_withCaseworkerFilter_shouldReturn200() {
    RestAssured.given()
        .spec(spec)
        .queryParam("userId", seededCaseworkerId)
    .when()
        .get("/applications")
    .then()
        .statusCode(200)
        .contentType(ContentType.JSON);
  }
}
