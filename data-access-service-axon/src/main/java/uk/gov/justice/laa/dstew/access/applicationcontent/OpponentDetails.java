package uk.gov.justice.laa.dstew.access.applicationcontent;

import jakarta.annotation.Nullable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Opponent Details pojo. */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@ExcludeFromGeneratedCodeCoverage
public class OpponentDetails implements Serializable {
  @Nullable private String opposableType;
  @Nullable private Opposable opposable;
}
