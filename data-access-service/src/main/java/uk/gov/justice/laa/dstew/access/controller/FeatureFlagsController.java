package uk.gov.justice.laa.dstew.access.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.config.FeatureProperties;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/**
 * REST controller to expose current feature flag values.
 *
 * <p>Used for Phase 1 POC validation of ConfigMaps-based feature flags. This controller provides a
 * simple endpoint to verify that flag values are correctly sourced and to confirm that pod restarts
 * pick up updated ConfigMap values without rebuilding the container image.
 *
 * <p>This controller is excluded from code coverage metrics.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/flags")
@ExcludeFromGeneratedCodeCoverage
public class FeatureFlagsController {

  private final FeatureProperties featureProperties;

  /**
   * Returns current feature flag values.
   *
   * @param serviceName optional service name from X-Service-Name header for request context
   * @return JSON object with current feature flag state
   */
  @Operation(
      summary = "Get current feature flag values",
      description =
          "Returns a JSON object containing the current values of all feature flags. "
              + "Useful for Phase 1 POC validation of ConfigMaps-based feature flags. "
              + "No authentication required.",
      tags = {"Feature Flags (POC)"})
  @ApiResponse(
      responseCode = "200",
      description = "Successfully retrieved feature flag values",
      content =
          @Content(
              mediaType = "application/json",
              schema =
                  @Schema(
                      type = "object",
                      example =
                          "{\"enableDevToken\":false,\"disableSecurity\":true,\"pocConfigmapEnabled\":false}")))
  @LogMethodResponse
  @GetMapping
  public ResponseEntity<Map<String, Boolean>> getFlags(
      @Parameter(
              name = "X-Service-Name",
              description = "Optional service name for request context",
              example = "XXCCCCCCCC")
          @RequestHeader(value = "X-Service-Name", required = false)
          String serviceName) {
    Map<String, Boolean> flagsMap = new LinkedHashMap<>();
    flagsMap.put("enableDevToken", featureProperties.enableDevToken());
    flagsMap.put("disableJpaAuditing", featureProperties.disableJpaAuditing());
    flagsMap.put("disableSecurity", featureProperties.disableSecurity());
    flagsMap.put("pocConfigmapEnabled", featureProperties.pocConfigmapEnabled());
    return ResponseEntity.ok(flagsMap);
  }
}
