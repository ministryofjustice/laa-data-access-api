package uk.gov.justice.laa.dstew.access.controller;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/flags")
public class FeatureFlagsController {

  @GetMapping
  public ResponseEntity<Map<String, Object>> flags() {
    Client client = OpenFeatureAPI.getInstance().getClient();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("pocEnabled", client.getBooleanValue("poc-enabled", false));
    payload.put("pocVariant", client.getStringValue("poc-variant", "control"));
    payload.put("pocVariantTwo", client.getStringValue("poc-variant-two", "control"));
    return ResponseEntity.ok(payload);
  }
}


