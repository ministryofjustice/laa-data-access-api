package uk.gov.justice.laa.dstew.access.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * ApplicationOffice.
 * Represents an office in the application create.
 * Using the same format as the OpenAPI generator to enable switch when schema stable.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@ExcludeFromGeneratedCodeCoverage
public class ApplicationOffice implements Serializable {
  @Nullable
  @Schema(name = "code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  private String code;

  /**
   * A container for additional, undeclared properties.
   * This is a holder for any undeclared properties as specified with
   * the 'additionalProperties' keyword in the OAS document.
   */
  @JsonAnyGetter
  private Map<String, Object> additionalOfficeData;

  /**
   * Set the additional (undeclared) property with the specified name and value.
   * If the property does not already exist, create it otherwise replace it.
   */
  @JsonAnySetter
  public ApplicationOffice putAdditionalProperty(String key, Object value) {
    if (this.additionalOfficeData == null) {
      this.additionalOfficeData = new HashMap<>();
    }
    this.additionalOfficeData.put(key, value);
    return this;
  }
}
