package uk.gov.justice.laa.dstew.access.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
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
 * ApplicationApplicant pojo.
 */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@ExcludeFromGeneratedCodeCoverage
public class ApplicationApplicant {

  @Nullable
  private List<Map<String, Object>> addresses;

  @JsonAnyGetter
  private Map<String, Object> additionalContent;

  /**
  * Set the additional (undeclared) property with the specified name and value.
  * If the property does not already exist, create it otherwise replace it.
  */
  @JsonAnySetter
  public uk.gov.justice.laa.dstew.access.model.ApplicationApplicant putAdditionalContent(String key, Object value) {
    if (this.additionalContent == null) {
      this.additionalContent = new HashMap<>();
    }
    this.additionalContent.put(key, value);
    return this;
  }
}
