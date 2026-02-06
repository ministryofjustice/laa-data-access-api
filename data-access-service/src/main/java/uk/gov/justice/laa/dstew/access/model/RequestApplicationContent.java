package uk.gov.justice.laa.dstew.access.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * RequestApplicationContent.
 * Using the same format as the OpenAPI generator to enable switch when schema stable
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@ExcludeFromGeneratedCodeCoverage
public class RequestApplicationContent implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  @Valid
  private @Nullable ApplicationStatus status;

  private @Nullable String applicationReference;
  @NotNull
  @Valid
  @Schema(name = "applicationContent", requiredMode = Schema.RequiredMode.REQUIRED)
  private ApplicationContent applicationContent;


  /**
   * A container for additional, undeclared properties.
   */
  @JsonAnyGetter
  private Map<String, Object> additionalParentApplicationContent;

  /**
   * Set the additional (undeclared) property with the specified name and value.
   * If the property does not already exist, create it otherwise replace it.
   */
  @JsonAnySetter
  public RequestApplicationContent putAdditionalProperty(String key, Object value) {
    if (this.additionalParentApplicationContent == null) {
      this.additionalParentApplicationContent = new HashMap<>();
    }
    this.additionalParentApplicationContent.put(key, value);
    return this;
  }

}

