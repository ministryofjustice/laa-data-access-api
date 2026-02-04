package uk.gov.justice.laa.dstew.access.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.ArrayList;
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
 * ApplicationContent pojo.
 * Using the same format as the OpenAPI generator to enable switch when schema stable
 */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@ExcludeFromGeneratedCodeCoverage
public class ApplicationContent implements Serializable {

  private static final long serialVersionUID = 1L;
  @NotNull
  @Valid
  @Schema(name = "id", requiredMode = Schema.RequiredMode.REQUIRED)
  private UUID id;

  @NotNull
  @Valid
  @Size(min = 1)
  @Schema(name = "proceedings", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("proceedings")
  private List<Proceeding> proceedings = new ArrayList<>();

  @NotNull
  @Schema(name = "submittedAt", requiredMode = Schema.RequiredMode.REQUIRED)
  private String submittedAt;

  private @Nullable String applicationRef;

  @Nullable
  @Valid
  @Schema(name = "office", requiredMode = Schema.RequiredMode.REQUIRED)
  private ApplicationOffice office;

  /**
   * A container for additional, undeclared properties.
   * This is a holder for any undeclared properties as specified with
   * the 'additionalProperties' keyword in the OAS document.
   */
  @JsonAnyGetter
  private Map<String, Object> additionalApplicationContent;

  /**
   * Set the additional (undeclared) property with the specified name and value.
   * If the property does not already exist, create it otherwise replace it.
   */
  @JsonAnySetter
  public ApplicationContent putAdditionalApplicationContent(String key, Object value) {
    if (this.additionalApplicationContent == null) {
      this.additionalApplicationContent = new HashMap<>();
    }
    this.additionalApplicationContent.put(key, value);
    return this;
  }


}

