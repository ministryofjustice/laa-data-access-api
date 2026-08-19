package uk.gov.justice.laa.dstew.access.applicationcontent;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Client information. Matches schema/common/Client.json. */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@ExcludeFromGeneratedCodeCoverage
public class ApplicationClient implements Serializable {

  @Nullable private String firstName;
  @Nullable private String lastName;
  @Nullable private String lastNameAtBirth;
  @Nullable private LocalDate dateOfBirth;
  @Nullable private Boolean hasNationalInsuranceNumber;
  @Nullable private String nationalInsuranceNumber;
  @Nullable private Boolean appliedPreviously;
  @Nullable private String previousApplicationId;
  @Nullable private List<ApplicationAddress> addresses;
  @Nullable private String relationshipToInvolvedChildren;
}
