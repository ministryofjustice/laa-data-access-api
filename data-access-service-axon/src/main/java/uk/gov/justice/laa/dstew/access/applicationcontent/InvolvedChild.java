package uk.gov.justice.laa.dstew.access.applicationcontent;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Involved child. Matches schema/common/Child.json. */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@ExcludeFromGeneratedCodeCoverage
public class InvolvedChild implements Serializable {

  @Nullable private UUID id;
  @Nullable private String fullName;
  @Nullable private LocalDate dateOfBirth;
}
