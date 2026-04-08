package uk.gov.justice.laa.dstew.access.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Model to represent linked applications in the application content.
 * 
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@ExcludeFromGeneratedCodeCoverage
public class LinkedApplication {
  @NotNull
  @Schema(name = "leadApplicationId", requiredMode = Schema.RequiredMode.REQUIRED)
  private UUID leadApplicationId;
    
  @NotNull
  @Schema(name = "associatedApplicationId", requiredMode = Schema.RequiredMode.REQUIRED)
  private UUID associatedApplicationId;
}
