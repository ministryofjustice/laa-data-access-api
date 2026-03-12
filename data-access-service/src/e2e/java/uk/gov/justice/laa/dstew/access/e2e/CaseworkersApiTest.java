package uk.gov.justice.laa.dstew.access.e2e;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CaseworkersApiTest extends BaseApiTest {

  @BeforeAll
  static void seedCaseworker() throws Exception {
    seeder.insertCaseworker("e2e-caseworker");
  }

  @Test
  void getCaseworkers_shouldReturn200AndMatchSpec() {
    RestAssured.given()
        .spec(spec)
    .when()
        .get("/caseworkers")
    .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("size()", greaterThanOrEqualTo(1));
  }
}
