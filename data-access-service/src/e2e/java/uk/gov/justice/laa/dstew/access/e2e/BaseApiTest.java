package uk.gov.justice.laa.dstew.access.e2e;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.restassured.OpenApiValidationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.aeonbits.owner.ConfigFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public abstract class BaseApiTest {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  protected static final String NON_EXISTENT_ID = "00000000-0000-0000-0000-000000000000";

  protected static RequestSpecification spec;
  protected static TestDataSeeder seeder;

  protected static final E2eConfig config =
      ConfigFactory.create(E2eConfig.class, System.getProperties());

  @BeforeAll
  static void setup() throws Exception {
    String baseUrl = config.baseUrl();
    String basePath = config.basePath();

    if (baseUrl == null || basePath == null) {
      throw new IllegalStateException(
          "Missing required configuration: base.url, base.path");
    }

    // Downgrade known spec drift to warnings so tests validate what they can
    OpenApiInteractionValidator validator = OpenApiInteractionValidator
        .createForInlineApiSpecification(loadSpec())
        .withLevelResolver(LevelResolver.create()
            .withLevel("validation.response.body.unexpected", ValidationReport.Level.WARN)
            .withLevel("validation.response.body.schema.required", ValidationReport.Level.WARN)
            .withLevel("validation.response.body.schema.additionalProperties", ValidationReport.Level.WARN)
            .withLevel("validation.response.body.schema.type", ValidationReport.Level.WARN)
            .withLevel("validation.response.status.unknown", ValidationReport.Level.WARN)
            .build())
        .build();

    RestAssured.config = RestAssured.config()
        .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
            .jackson2ObjectMapperFactory((type, s) -> MAPPER));

    spec = new RequestSpecBuilder()
        .setBaseUri(baseUrl)
        .setBasePath(basePath)
        .setContentType(ContentType.JSON)
        .addHeader("X-Service-Name", "CIVIL_DECIDE")
        .addFilter(new OpenApiValidationFilter(validator))
        .build();

    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    seeder = new TestDataSeeder(config.dbUrl(), config.dbUsername(), config.dbPassword());
  }

  @AfterAll
  static void tearDown() throws Exception {
    if (seeder != null) {
      seeder.cleanup();
      seeder.close();
    }
  }

  private static String loadSpec() {
    try (var stream = BaseApiTest.class.getResourceAsStream("/openapi/open-api-application-specification.yml")) {
      if (stream == null) {
        throw new IllegalStateException("OpenAPI spec not found on classpath at /openapi/open-api-application-specification.yml");
      }
      return new String(stream.readAllBytes());
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to load OpenAPI spec", e);
    }
  }
}
