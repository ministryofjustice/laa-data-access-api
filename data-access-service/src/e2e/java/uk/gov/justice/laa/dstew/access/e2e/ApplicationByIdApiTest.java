package uk.gov.justice.laa.dstew.access.e2e;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator.createDefault;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationUpdateRequestGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;

@TestMethodOrder(OrderAnnotation.class)
class ApplicationByIdApiTest extends BaseApiTest {

  private static ApplicationEntity seededApp;
  private static String createdApplicationId;

  @BeforeAll
  static void seedApplication() throws Exception {
    seededApp = new ApplicationEntityGenerator().createDefault();
    var individual = new IndividualEntityGenerator().createDefault();
    UUID individualId = seeder.insertIndividual(individual);
    UUID applicationId = seeder.insertApplication(seededApp);
    seeder.linkIndividualToApplication(applicationId, individualId);
    createdApplicationId = applicationId.toString();
  }

  @Test
  @Order(1)
  void getApplicationById_shouldReturn200AndMatchSeededData() {
    RestAssured.given()
        .spec(spec)
    .when()
        .get("/applications/{id}", createdApplicationId)
    .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("applicationId", equalTo(createdApplicationId))
        .body("status", equalTo(seededApp.getStatus().name()))
        .body("laaReference", equalTo(seededApp.getLaaReference()))
        .body("submittedAt", equalTo(seededApp.getSubmittedAt().toString()))
        .body("useDelegatedFunctions", equalTo(seededApp.getUsedDelegatedFunctions()))
        .body("autoGrant", equalTo(seededApp.getIsAutoGranted()))
        .body("isLead", equalTo(false))
        .body("applicationType", equalTo("INITIAL"))
        .body("provider", equalTo(seededApp.getOfficeCode()))
        .body("lastUpdated", notNullValue());
  }

  @Test
  @Order(2)
  void getApplicationById_withInvalidId_shouldReturn404() {
    RestAssured.given()
        .spec(spec)
    .when()
        .get("/applications/{id}", NON_EXISTENT_ID)
    .then()
        .statusCode(404);
  }

  @Test
  @Order(3)
  void updateApplication_shouldReflectChanges() {
    var updateRequest = createDefault(ApplicationUpdateRequestGenerator.class,
        b -> b.status(ApplicationStatus.APPLICATION_SUBMITTED));

    RestAssured.given()
        .spec(spec)
        .body(updateRequest)
    .when()
        .patch("/applications/{id}", createdApplicationId)
    .then()
        .statusCode(204);

    RestAssured.given()
        .spec(spec)
    .when()
        .get("/applications/{id}", createdApplicationId)
    .then()
        .statusCode(200)
        .body("status", equalTo(ApplicationStatus.APPLICATION_SUBMITTED.name()));
  }
}
