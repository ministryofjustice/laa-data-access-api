package uk.gov.justice.laa.dstew.access.applicationcontent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Opposable pojo. */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@ExcludeFromGeneratedCodeCoverage
public class Opposable {
  private @Nullable String firstName;
  private @Nullable String lastName;
  private @Nullable String name;
}
