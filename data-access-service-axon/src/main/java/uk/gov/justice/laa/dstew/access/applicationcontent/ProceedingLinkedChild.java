package uk.gov.justice.laa.dstew.access.applicationcontent;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Represents a link between a proceeding merits entry and an involved child. */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@ExcludeFromGeneratedCodeCoverage
public class ProceedingLinkedChild {

  @Nullable private UUID involvedChildId;

  @JsonAnyGetter private Map<String, Object> additionalContent;

  /** Set the additional (undeclared) property with the specified name and value. */
  @JsonAnySetter
  public ProceedingLinkedChild putAdditionalContent(String key, Object value) {
    if (this.additionalContent == null) {
      this.additionalContent = new HashMap<>();
    }
    this.additionalContent.put(key, value);
    return this;
  }
}
