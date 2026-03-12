package uk.gov.justice.laa.dstew.access.e2e;

import static org.hamcrest.Matchers.equalTo;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.aeonbits.owner.ConfigFactory;
import org.junit.jupiter.api.Test;

class HealthCheckApiTest {

  private static final E2eConfig config =
      ConfigFactory.create(E2eConfig.class, System.getProperties());

  @Test
  void actuatorHealth_shouldReturnUp() {
    RestAssured
        .given()
            .accept(ContentType.JSON)
            .baseUri(config.baseUrl())
            .basePath("/")
        .when()
            .get("/actuator/health")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
  }
}
