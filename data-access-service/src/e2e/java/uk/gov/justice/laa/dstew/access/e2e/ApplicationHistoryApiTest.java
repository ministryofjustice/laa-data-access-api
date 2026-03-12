package uk.gov.justice.laa.dstew.access.e2e;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ApplicationHistoryApiTest extends BaseApiTest {

  private static String seededApplicationId;

  @BeforeAll
  static void seedApplication() throws Exception {
    UUID appId = seeder.seedApplicationWithIndividual();
    seeder.insertDomainEvent(appId);
    seededApplicationId = appId.toString();
  }

  @Test
  void getApplicationHistory_shouldReturn200AndMatchSpec() {
    RestAssured.given()
        .spec(spec)
    .when()
        .get("/applications/{id}/history-search", seededApplicationId)
    .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("events.size()", greaterThanOrEqualTo(1));
  }

  @Test
  void getApplicationHistory_withInvalidId_shouldReturn200EmptyEvents() {
    RestAssured.given()
        .spec(spec)
    .when()
        .get("/applications/{id}/history-search", NON_EXISTENT_ID)
    .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("events.size()", equalTo(0));
  }
}
