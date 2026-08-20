package uk.gov.justice.laa.dstew.access.applicationcontent;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Provider details from the application content. */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@ExcludeFromGeneratedCodeCoverage
public class ApplicationProvider implements Serializable {

  @Nullable
  @Schema(name = "officeCode", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  private String officeCode;

  @Nullable
  @Schema(name = "contactEmail", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  private String contactEmail;
}
