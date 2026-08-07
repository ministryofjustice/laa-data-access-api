package uk.gov.justice.laa.dstew.access.applicationcontent;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** ApplicationContent pojo. Matches schema/1/ApplyApplication.json. */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@ExcludeFromGeneratedCodeCoverage
public class ApplicationContent implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @NotNull
  @Valid
  @Schema(name = "id", requiredMode = Schema.RequiredMode.REQUIRED)
  private UUID id;

  @Nullable
  @Schema(name = "createdAt", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  private String createdAt;

  @NotNull
  @Schema(name = "submittedAt", requiredMode = Schema.RequiredMode.REQUIRED)
  private String submittedAt;

  @Nullable
  @Valid
  @Schema(name = "provider", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  private ApplicationProvider provider;

  @Nullable
  @Valid
  @Schema(name = "client", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  private ApplicationClient client;

  @Nullable
  @Valid
  @Schema(name = "proceedings", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("proceedings")
  private List<Proceeding> proceedings = new ArrayList<>();

  @Nullable
  @Valid
  @Schema(name = "opponents", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("opponents")
  private List<Opponent> opponents;

  @Nullable private String status;

  @Nullable
  @Schema(name = "allLinkedApplications", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  private List<LinkedApplication> allLinkedApplications;
}
