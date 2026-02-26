package uk.gov.justice.laa.dstew.access.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * ApplicationMerits pojo.
 * Using the same format as the OpenAPI generator to enable switch when schema stable
 */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@ExcludeFromGeneratedCodeCoverage
public class ApplicationMerits {

  @Nullable
  private List<OpponentDetails> opponents = new ArrayList<>();
}
