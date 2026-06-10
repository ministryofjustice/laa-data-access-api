package uk.gov.justice.laa.dstew.access.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

/**
 * Injects named request-body examples into the {@code createApplication} Swagger UI operation so
 * that users see realistic sample values instead of generic {@code "string"} / {@code {}} placeholders.
 *
 * <p>Examples are built from real model objects and serialised via Jackson, so they always reflect
 * the actual field structure without any manual JSON maintenance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateApplicationExamplesCustomizer implements OperationCustomizer {

  private static final String CREATE_APPLICATION_OPERATION_ID = "createApplication";
  private static final String MEDIA_TYPE = org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

  private final ObjectMapper objectMapper;

  private static final List<ExampleVariant> VARIANTS = List.of(
      new ExampleVariant(
          "apply_minimal",
          "APPLY — minimal (only required fields)",
          () -> buildRequest(
              Map.of(
                  "id", "550e8400-e29b-41d4-a716-446655440100",
                  "submittedAt", "2024-03-21T09:00:00Z"
              ),
              ApplicationStatus.APPLICATION_IN_PROGRESS,
              "LAA12345678",
              "Jane", "Smith", "1990-01-15",
              Map.of("niNumber", "AB123456C")
          )
      ),
      new ExampleVariant(
          "apply_full",
          "APPLY — all fields including proceedings, office and linked application",
          () -> buildRequest(
              // All fields from ApplyApplication.json and its referenced common schemas
              linkedHashMapOf(
                  "id", "550e8400-e29b-41d4-a716-446655440101",
                  "submittedAt", "2024-03-20T10:30:00Z",
                  "status", "SUBMITTED",
                  "laaReference", "LAA87654321",
                  // ApplicationOffice.json: { code }
                  "office", Map.of("code", "1L382A"),
                  // Proceeding.json: { id, categoryOfLaw, matterType, usedDelegatedFunctions, leadProceeding, description }
                  "proceedings", List.of(
                      linkedHashMapOf(
                          "id", "660e8400-e29b-41d4-a716-446655440001",
                          "categoryOfLaw", "FAMILY",
                          "matterType", "ASYLUM",
                          "usedDelegatedFunctions", true,
                          "leadProceeding", true,
                          "description", "Immigration and Asylum"
                      )
                  ),
                  // LinkedApplication.json: { leadApplicationId, associatedApplicationId }
                  "allLinkedApplications", List.of(
                      Map.of(
                          "leadApplicationId", "770e8400-e29b-41d4-a716-446655440002",
                          "associatedApplicationId", "880e8400-e29b-41d4-a716-446655440003"
                      )
                  )
              ),
              ApplicationStatus.APPLICATION_IN_PROGRESS,
              "LAA87654321",
              "John", "Doe", "1985-06-20",
              Map.of("niNumber", "CD654321E")
          )
      ),
      new ExampleVariant(
          "css_full",
          "CSS — all fields (laaReference required inside applicationContent)",
          () -> buildRequest(
              // All fields from CssApplication.json (laaReference is required for CSS)
              linkedHashMapOf(
                  "id", "550e8400-e29b-41d4-a716-446655440102",
                  "submittedAt", "2024-03-19T14:00:00Z",
                  "status", "SUBMITTED",
                  "laaReference", "LAA99999999",
                  "office", Map.of("code", "2X100B"),
                  "proceedings", List.of(
                      linkedHashMapOf(
                          "id", "660e8400-e29b-41d4-a716-446655440004",
                          "categoryOfLaw", "CRIME",
                          "matterType", "SERIOUS_CRIME",
                          "usedDelegatedFunctions", false,
                          "leadProceeding", true,
                          "description", "Serious crime proceedings"
                      )
                  ),
                  "allLinkedApplications", List.of(
                      Map.of(
                          "leadApplicationId", "770e8400-e29b-41d4-a716-446655440005",
                          "associatedApplicationId", "880e8400-e29b-41d4-a716-446655440006"
                      )
                  )
              ),
              ApplicationStatus.APPLICATION_SUBMITTED,
              "LAA99999999",
              "Robert", "Johnson", "1988-12-20",
              Map.of("niNumber", "EF789012G")
          )
      )
  );

  /**
   * Builds a {@link LinkedHashMap} from alternating key/value pairs, preserving insertion order
   * so the Swagger UI example fields appear in a predictable sequence.
   */
  @SuppressWarnings("unchecked")
  private static <K, V> Map<K, V> linkedHashMapOf(Object... entries) {
    LinkedHashMap<K, V> map = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      map.put((K) entries[i], (V) entries[i + 1]);
    }
    return map;
  }

  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    if (!CREATE_APPLICATION_OPERATION_ID.equals(operation.getOperationId())) {
      return operation;
    }

    Map<String, Example> examples = new LinkedHashMap<>();
    for (ExampleVariant variant : VARIANTS) {
      ApplicationCreateRequest request = variant.requestBuilder().get();
      examples.put(variant.key(), buildExample(variant.summary(), request));
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

  private static ApplicationCreateRequest buildRequest(
      Map<String, Object> applicationContent,
      ApplicationStatus status,
      String laaReference,
      String firstName,
      String lastName,
      String dateOfBirth,
      Map<String, Object> individualDetails) {
    ApplicationCreateRequest request = new ApplicationCreateRequest();
    request.setStatus(status);
    request.setApplicationContent(applicationContent);
    request.setLaaReference(laaReference);
    request.setIndividuals(List.of(clientIndividual(firstName, lastName, dateOfBirth, individualDetails)));
    return request;
  }

  private static Individual clientIndividual(
      String firstName, String lastName, String dateOfBirth, Map<String, Object> details) {
    Individual individual = new Individual();
    individual.setFirstName(firstName);
    individual.setLastName(lastName);
    individual.setDateOfBirth(LocalDate.parse(dateOfBirth));
    individual.setType(IndividualType.CLIENT);
    individual.setDetails(details);
    return individual;
  }

  private Example buildExample(String summary, Object value) {
    try {
      Example example = new Example();
      example.setSummary(summary);
      String json = objectMapper.writeValueAsString(value);
      example.setValue(objectMapper.readValue(json, Object.class));
      return example;
    } catch (Exception e) {
      log.error("Failed to serialise OpenAPI example '{}': {}", summary, e.getMessage(), e);
      Example fallback = new Example();
      fallback.setSummary(summary);
      return fallback;
    }
  }

  private record ExampleVariant(
      String key,
      String summary,
      Supplier<ApplicationCreateRequest> requestBuilder) {}
}
