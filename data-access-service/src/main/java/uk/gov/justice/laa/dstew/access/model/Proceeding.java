package uk.gov.justice.laa.dstew.access.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Proceeding.
 * Using the same format as the OpenAPI generator to enable switch when schema stable
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@ExcludeFromGeneratedCodeCoverage
public class Proceeding implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;
  @NotNull
  @Valid
  @Schema(name = "id", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("id")
  private UUID id;

  private @Nullable String categoryOfLaw;

  private @Nullable String matterType;

  private @Nullable Boolean usedDelegatedFunctions;
  @NotNull
  @Schema(name = "leadProceeding", requiredMode = Schema.RequiredMode.REQUIRED)
  private Boolean leadProceeding;

  @NotNull
  @Schema(name = "description", requiredMode = Schema.RequiredMode.REQUIRED)
  private String description;

  @Nullable private String meaning;

  @Nullable private LocalDate usedDelegatedFunctionsOn;

  @Nullable private String substantiveLevelOfServiceName;

  @Nullable private String substantiveCostLimitation;

  @Nullable private List<Map<String, Object>> scopeLimitations;

  /**
   * A container for additional, undeclared properties.
   * This is a holder for any undeclared properties as specified with
   * the 'additionalProperties' keyword in the OAS document.
   */
  @JsonAnyGetter
  private Map<String, Object> additionalProceedingsData;

  /**
   * Set the additional (undeclared) property with the specified name and value.
   * If the property does not already exist, create it otherwise replace it.
   */
  @JsonAnySetter
  public Proceeding putAdditionalProperty(String key, Object value) {
    if (this.additionalProceedingsData == null) {
      this.additionalProceedingsData = new HashMap<>();
    }
    this.additionalProceedingsData.put(key, value);
    return this;
  }


}

