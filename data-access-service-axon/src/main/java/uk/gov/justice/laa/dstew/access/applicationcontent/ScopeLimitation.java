package uk.gov.justice.laa.dstew.access.applicationcontent;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Scope limitation for a proceeding. Matches schema/common/ScopeLimitation.json. */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@ExcludeFromGeneratedCodeCoverage
public class ScopeLimitation implements Serializable {

  @Nullable private UUID id;
  @Nullable private String code;
  @Nullable private String type;
  @Nullable private String meaning;
  @Nullable private String description;
}
