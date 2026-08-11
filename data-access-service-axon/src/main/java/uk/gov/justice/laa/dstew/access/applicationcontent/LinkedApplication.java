package uk.gov.justice.laa.dstew.access.applicationcontent;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Linked application. Matches schema/common/LinkedApplication.json. */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@ExcludeFromGeneratedCodeCoverage
public class LinkedApplication implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Nullable private UUID id;

  @NotNull
  @Schema(name = "leadApplicationId", requiredMode = Schema.RequiredMode.REQUIRED)
  private UUID leadApplicationId;

  @NotNull
  @Schema(name = "associatedApplicationId", requiredMode = Schema.RequiredMode.REQUIRED)
  private UUID associatedApplicationId;

  @Nullable private UUID targetApplicationId;

  @Nullable private String linkTypeCode;

  @Nullable private String createdAt;

  @Nullable private String updatedAt;

  @Nullable private Boolean confirmLink;

  @JsonAnyGetter private Map<String, Object> additionalData;

  /** Stores an additional unmapped linked-application property. */
  @JsonAnySetter
  public LinkedApplication putAdditionalProperty(String key, Object value) {
    if (this.additionalData == null) {
      this.additionalData = new HashMap<>();
    }
    this.additionalData.put(key, value);
    return this;
  }
}
