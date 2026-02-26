package uk.gov.justice.laa.dstew.access.model;

import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Opponent Details pojo.
 * Using the same format as the OpenAPI generator to enable switch when schema stable
 */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@ExcludeFromGeneratedCodeCoverage
public class OpponentDetails {

  @Nullable private Opposable opposable;
}
