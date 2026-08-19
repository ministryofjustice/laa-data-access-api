package uk.gov.justice.laa.dstew.access.applicationcontent;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Opponent information. Matches schema/common/Opponent.json. */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@ExcludeFromGeneratedCodeCoverage
public class Opponent implements Serializable {

  @Nullable private String opponentType;
  @Nullable private String firstName;
  @Nullable private String lastName;
  @Nullable private String organisationName;
}
