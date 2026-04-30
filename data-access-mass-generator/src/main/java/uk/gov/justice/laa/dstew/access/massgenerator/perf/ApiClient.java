package uk.gov.justice.laa.dstew.access.massgenerator.perf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Thin wrapper around Spring {@link WebClient} for the two performance-test operations:
 *
 * <ol>
 *   <li>POST /api/v0/applications – create an application, return its UUID
 *   <li>PATCH /api/v0/applications/{id}/decision – make a decision
 * </ol>
 *
 * <p>Each method records wall-clock time and stores the nanosecond duration in the supplied {@link
 * PerformanceMetrics} instance.
 */
@Component
public class ApiClient {

  private final PerformanceTestProperties props;
  private final ObjectMapper objectMapper;
  private WebClient webClient;

  public ApiClient(PerformanceTestProperties props, ObjectMapper objectMapper) {
    this.props = props;
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  void init() {
    this.webClient =
        WebClient.builder()
            .baseUrl(props.getBaseUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getBearerToken())
            .defaultHeader("X-Service-Name", props.getXServiceName())
            .build();
  }

  /**
   * POST /api/v0/applications.
   *
   * @param body the serialisable request body (e.g. an {@code ApplicationCreateRequest} or Map)
   * @param metrics collector to record latency or errors into
   * @return the application UUID parsed from the {@code Location} response header, or {@code null}
   *     if the call failed
   */
  public UUID createApplication(Object body, PerformanceMetrics metrics) {
    long start = System.nanoTime();
    try {
      var response =
          webClient
              .post()
              .uri("/api/v0/applications")
              .header("X-Service-Name", "CIVIL_DECIDE")
              .bodyValue(body)
              .retrieve()
              .toBodilessEntity()
              .block();

      long elapsed = System.nanoTime() - start;
      metrics.record(elapsed);

      if (response == null) return null;

      // Extract UUID from Location header, e.g. /api/v0/applications/{uuid}
      var location = response.getHeaders().getLocation();
      if (location != null) {
        String path = location.getPath();
        String uuidStr = path.substring(path.lastIndexOf('/') + 1);
        try {
          return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException ignored) {
          /* fall through */
        }
      }
      return null;

    } catch (WebClientResponseException e) {
      metrics.recordError();
      //      System.err.printf(
      //          "[ApiClient] POST /api/v0/applications failed: HTTP %d%n  Response body: %s%n
      // Request body: %s%n",
      //          e.getStatusCode().value(),
      //          e.getResponseBodyAsString(),
      //          toJson(body));
      return null;
    } catch (Exception e) {
      metrics.recordError();
      //      System.err.printf(
      //          "[ApiClient] POST /api/v0/applications failed: %s%n  Request body: %s%n",
      //          e.getMessage(), toJson(body));
      return null;
    }
  }

  /**
   * PATCH /api/v0/applications/{id}/decision.
   *
   * @param applicationId the application UUID
   * @param body the serialisable decision request body
   * @param metrics collector to record latency or errors into
   */
  public void makeDecision(UUID applicationId, Object body, PerformanceMetrics metrics) {
    long start = System.nanoTime();
    try {
      webClient
          .patch()
          .uri("/api/v0/applications/{id}/decision", applicationId)
          .header("X-Service-Name", "CIVIL_DECIDE")
          .bodyValue(body)
          .retrieve()
          .toBodilessEntity()
          .block();

      metrics.record(System.nanoTime() - start);

    } catch (WebClientResponseException e) {
      metrics.recordError();
      //      System.err.printf(
      //          "[ApiClient] PATCH /api/v0/applications/%s/decision failed: HTTP %d%n  Response
      // body: %s%n  Request body: %s%n",
      //          applicationId,
      //          e.getStatusCode().value(),
      //          e.getResponseBodyAsString(),
      //          toJson(body));
    } catch (Exception e) {
      //      metrics.recordError();
      //      System.err.printf(
      //          "[ApiClient] PATCH /api/v0/applications/%s/decision failed: %s%n  Request body:
      // %s%n",
      //          applicationId, e.getMessage(), toJson(body));
    }
  }

  /**
   * GET /api/v0/applications/{id} and return the list of proceedingIds from the response. Returns
   * an empty list if the call fails.
   */
  public List<UUID> getApplicationProceedingIds(UUID applicationId) {
    try {
      String body =
          webClient
              .get()
              .uri("/api/v0/applications/{id}", applicationId)
              .header("X-Service-Name", "CIVIL_DECIDE")
              .retrieve()
              .bodyToMono(String.class)
              .block();

      if (body == null) return List.of();

      List<UUID> ids = new ArrayList<>();
      JsonNode proceedings = objectMapper.readTree(body).path("proceedings");
      if (proceedings.isArray()) {
        for (JsonNode p : proceedings) {
          JsonNode idNode = p.path("proceedingId");
          if (!idNode.isMissingNode()) {
            ids.add(UUID.fromString(idNode.asText()));
          }
        }
      }
      return ids;

    } catch (WebClientResponseException e) {
      System.err.printf(
          "[ApiClient] GET /api/v0/applications/%s failed: HTTP %d – %s%n",
          applicationId, e.getStatusCode().value(), e.getResponseBodyAsString());
      return List.of();
    } catch (Exception e) {
      System.err.printf(
          "[ApiClient] GET /api/v0/applications/%s failed: %s%n", applicationId, e.getMessage());
      return List.of();
    }
  }

  private String toJson(Object body) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
    } catch (JsonProcessingException e) {
      return "<could not serialise: " + e.getMessage() + ">";
    }
  }
}
