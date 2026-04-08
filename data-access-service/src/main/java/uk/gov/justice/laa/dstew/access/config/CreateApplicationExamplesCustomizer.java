package uk.gov.justice.laa.dstew.access.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplyApplication;
import uk.gov.justice.laa.dstew.access.model.ApplyApplication1;
import uk.gov.justice.laa.dstew.access.model.BaseApplicationContent;
import uk.gov.justice.laa.dstew.access.model.CreateProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.DecideApplication;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

/**
 * Dynamically generates request body examples for operations with applicationContent
 * by serialising real model objects, so examples always reflect the actual schema.
 *
 * <p>
 * Supports multiple operations and variants through a configurable supplier pattern,
 * similar to DataGenerators for consistency with the codebase conventions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateApplicationExamplesCustomizer implements OperationCustomizer {

  private static final String OPERATION_ID = "createApplication";
  private static final String MEDIA_TYPE = org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

  private final ObjectMapper objectMapper;

  // Define variants using supplier pattern (similar to DataGenerators)
  private static final List<ExampleVariant> VARIANTS = List.of(
      ExampleVariant.builder()
          .key("v1_apply_example")
          .description("V1 Apply — applicationContent is ApplyApplication (objectType=apply)")
          .requestBuilder(() -> buildRequest(
              () -> {
                ApplyApplication content = new ApplyApplication();
                content.setObjectType("apply");
                content.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440100"));
                content.setSubmittedAt(OffsetDateTime.parse("2024-03-21T09:00:00Z"));
                return content;
              },
              ApplicationStatus.APPLICATION_IN_PROGRESS,
              "LAA12345678",
              "John", "Doe", "1990-01-01"
          ))
          .build(),
      ExampleVariant.builder()
          .key("v2_apply_example")
          .description("V2 Apply — applicationContent is ApplyApplication1 (objectType=applyV2)")
          .requestBuilder(() -> buildRequest(
              () -> {
                CreateProceedingRequest proceeding = new CreateProceedingRequest();
                proceeding.setId(UUID.fromString("660e8400-e29b-41d4-a716-446655440001"));
                proceeding.setCategoryOfLaw("FAMILY");
                proceeding.setMatterType("ASYLUM");

                ApplyApplication1 content = new ApplyApplication1();
                content.setObjectType("applyV2");
                content.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440101"));
                content.setSubmittedAt(OffsetDateTime.parse("2024-03-20T10:30:00Z"));
                content.setProceedings(List.of(proceeding));
                return content;
              },
              ApplicationStatus.APPLICATION_IN_PROGRESS,
              "LAA87654321",
              "Jane", "Smith", "1992-05-15"
          ))
          .build(),
      ExampleVariant.builder()
          .key("decide_example")
          .description("Decide — applicationContent is DecideApplication (objectType=decide)")
          .requestBuilder(() -> buildRequest(
              () -> {
                DecideApplication content = new DecideApplication();
                content.setObjectType("decide");
                content.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440102"));
                content.setSubmittedAt(OffsetDateTime.parse("2024-03-19T14:00:00Z"));
                content.setOffice("LON001");
                return content;
              },
              ApplicationStatus.APPLICATION_SUBMITTED,
              "LAA11111111",
              "Robert", "Johnson", "1988-12-20"
          ))
          .build()
  );

  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    if (!OPERATION_ID.equals(operation.getOperationId())) {
      return operation;
    }

    // Generate examples from configured variants
    Map<String, Example> examples = new LinkedHashMap<>();
    for (ExampleVariant variant : VARIANTS) {
      ApplicationCreateRequest request = variant.requestBuilder.get();
      examples.put(variant.key, buildExample(variant.description, request));
    }

    RequestBody requestBody = operation.getRequestBody();
    if (requestBody == null) {
      requestBody = new RequestBody();
      operation.setRequestBody(requestBody);
    }

    Content content = requestBody.getContent();
    if (content == null) {
      content = new Content();
      requestBody.setContent(content);
    }

    MediaType mediaType = content.computeIfAbsent(MEDIA_TYPE, key -> {
      MediaType mt = new MediaType();
      mt.setSchema(new Schema<>().$ref("#/components/schemas/ApplicationCreateRequest"));
      return mt;
    });

    mediaType.setExamples(examples);

    return operation;
  }

  // --- Reusable generic builder for basic application requests ---
  private static ApplicationCreateRequest buildGenericRequest(
      BaseApplicationContent content,
      ApplicationStatus status,
      String laaReference,
      String firstName,
      String lastName,
      String dateOfBirth) {
    return ApplicationCreateRequest.builder()
        .status(status)
        .applicationContent(content)
        .laaReference(laaReference)
        .individuals(List.of(clientIndividual(firstName, lastName, dateOfBirth)))
        .build();
  }

  // --- Single reusable method that accepts content initializer as a supplier ---
  private static ApplicationCreateRequest buildRequest(
      Supplier<BaseApplicationContent> contentInitializer,
      ApplicationStatus status,
      String laaReference,
      String firstName,
      String lastName,
      String dateOfBirth) {
    return buildGenericRequest(
        contentInitializer.get(),
        status,
        laaReference,
        firstName,
        lastName,
        dateOfBirth
    );
  }

  private static IndividualCreateRequest clientIndividual(
      String firstName, String lastName, String dateOfBirth) {
    IndividualCreateRequest individual = new IndividualCreateRequest();
    individual.setFirstName(firstName);
    individual.setLastName(lastName);
    individual.setDateOfBirth(LocalDate.parse(dateOfBirth));
    individual.setType(IndividualType.CLIENT);
    individual.setDetails(Map.of());
    return individual;
  }

  private Example buildExample(String summary, Object value) {
    try {
      Example example = new Example();
      example.setSummary(summary);
      // Serialise then re-parse so the value is a structured node, not a string
      String json = objectMapper.writeValueAsString(value);
      Object deserialized = objectMapper.readValue(json, Object.class);

      if (deserialized == null) {
        log.warn("Deserialised value is null for example: {}", summary);
      }

      example.setValue(deserialized);
      return example;
    } catch (Exception e) {
      log.error("Failed to serialise OpenAPI example for createApplication: {}", e.getMessage(), e);
      Example fallback = new Example();
      fallback.setSummary(summary);
      return fallback;
    }
  }

  /**
   * Configuration for an example variant.
   * Similar to DataGenerator pattern - cleanly separates variant definition from logic.
   */
  private static class ExampleVariant {
    private final String key;
    private final String description;
    private final Supplier<ApplicationCreateRequest> requestBuilder;

    ExampleVariant(String key, String description, Supplier<ApplicationCreateRequest> requestBuilder) {
      this.key = key;
      this.description = description;
      this.requestBuilder = requestBuilder;
    }

    static ExampleVariantBuilder builder() {
      return new ExampleVariantBuilder();
    }

    static class ExampleVariantBuilder {
      private String key;
      private String description;
      private Supplier<ApplicationCreateRequest> requestBuilder;

      ExampleVariantBuilder key(String key) {
        this.key = key;
        return this;
      }

      ExampleVariantBuilder description(String description) {
        this.description = description;
        return this;
      }

      ExampleVariantBuilder requestBuilder(Supplier<ApplicationCreateRequest> requestBuilder) {
        this.requestBuilder = requestBuilder;
        return this;
      }

      ExampleVariant build() {
        return new ExampleVariant(key, description, requestBuilder);
      }
    }
  }
}



