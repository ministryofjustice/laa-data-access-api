package uk.gov.justice.laa.dstew.access.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Opponent Details pojo.
 */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@ExcludeFromGeneratedCodeCoverage
public class OpponentDetails {

  @Nullable private Opposable opposable;

  /**
   * A container for additional, undeclared properties.
   * This is a holder for any undeclared properties as specified with
   * the 'additionalProperties' keyword in the OAS document.
   */
  @JsonAnyGetter
  private Map<String, Object> additionalContent;

  /**
   * Set the additional (undeclared) property with the specified name and value.
   * If the property does not already exist, create it otherwise replace it.
   */
  @JsonAnySetter
  public OpponentDetails putAdditionalContent(String key, Object value) {
    if (this.additionalContent == null) {
      this.additionalContent = new HashMap<>();
    }
    this.additionalContent.put(key, value);
    return this;
  }
}
