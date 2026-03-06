package uk.gov.justice.laa.dstew.access.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * ApplicationMerits pojo.
 */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@ExcludeFromGeneratedCodeCoverage
public class ApplicationMerits {

  @Nullable
  private List<OpponentDetails> opponents = new ArrayList<>();

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
  public ApplicationMerits putAdditionalApplicationContent(String key, Object value) {
    if (this.additionalApplicationContent == null) {
      this.additionalApplicationContent = new HashMap<>();
    }
    this.additionalApplicationContent.put(key, value);
    return this;
  }
}
