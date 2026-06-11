package uk.gov.justice.laa.dstew.access.controller;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.config.FeatureProperties;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

/**
 * Exposes effective feature flag values for test and diagnostics only.
 *
 * <p>This endpoint is intended to verify environment wiring in preview/ephemeral deployments and
 * should not be treated as a public runtime contract.
 */
@ExcludeFromGeneratedCodeCoverage
@RestController
@RequiredArgsConstructor
public class FeatureFlagsController {

  private final FeatureProperties featureProperties;

  /**
   * Returns current resolved feature flag values.
   *
   * <p>Test purpose only: this response is used to confirm feature flag propagation from deployment
   * configuration into application runtime.
   *
   * @return map of feature flag names to effective boolean values
   */
  @GetMapping(path = "/feature-flags", produces = "application/json")
  public ResponseEntity<Map<String, Boolean>> getFeatureFlags(
      @RequestHeader(value = "X-Service-Name") ServiceName serviceName) {
    Map<String, Boolean> flags =
        Map.of(
            "enableDevToken", featureProperties.enableDevToken(),
            "disableJpaAuditing", featureProperties.disableJpaAuditing(),
            "disableSecurity", featureProperties.disableSecurity(),
            "exampleFeatureFlag", featureProperties.exampleFeatureFlag());
    return ResponseEntity.ok(flags);
  }
}
