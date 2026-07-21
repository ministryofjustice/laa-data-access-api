package uk.gov.justice.laa.dstew.access.applicationcontent;

import java.io.Serializable;
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
public class Opposable implements Serializable {
  private @Nullable String firstName;
  private @Nullable String lastName;
  private @Nullable String name;
}
