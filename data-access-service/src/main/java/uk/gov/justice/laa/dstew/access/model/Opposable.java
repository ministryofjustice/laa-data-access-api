package uk.gov.justice.laa.dstew.access.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Opposable pojo.
 * Using the same format as the OpenAPI generator to enable switch when schema stable
 */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@ExcludeFromGeneratedCodeCoverage
public class Opposable {
  private @Nullable String opposableType;
  private @Nullable String firstName;
  private @Nullable String lastName;
  private @Nullable String name;
}
