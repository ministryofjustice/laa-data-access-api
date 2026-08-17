package uk.gov.justice.laa.dstew.access.applicationcontent;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Proceeding. Matches schema/common/Proceeding.json. */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@ExcludeFromGeneratedCodeCoverage
public class Proceeding implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @NotNull
  @Valid
  @Schema(name = "id", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("id")
  private UUID id;

  @NotNull
  @Schema(name = "leadProceeding", requiredMode = Schema.RequiredMode.REQUIRED)
  private Boolean leadProceeding;

  @NotNull
  @Schema(name = "code", requiredMode = Schema.RequiredMode.REQUIRED)
  private String code;

  @Nullable private String meaning;

  @NotNull
  @Schema(name = "description", requiredMode = Schema.RequiredMode.REQUIRED)
  private String description;

  @Nullable private String matterType;

  @Nullable private String matterTypeCode;

  @Nullable private String categoryOfLaw;

  @Nullable private String categoryOfLawCode;

  @Nullable private String clientInvolvementType;

  @Nullable private String clientInvolvementTypeCode;

  private @Nullable Boolean usedDelegatedFunctions;

  @Nullable private LocalDate delegatedFunctionsDate;

  @Nullable
  @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
  private BigDecimal delegatedFunctionsCostLimitation;

  @Nullable
  @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
  private BigDecimal substantiveCostLimitation;

  @Nullable private Integer substantiveLevelOfService;

  @Nullable private String substantiveLevelOfServiceName;

  @Nullable private Integer emergencyLevelOfService;

  @Nullable private String emergencyLevelOfServiceName;

  @Nullable
  @Valid
  @Schema(name = "scopeLimitations", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("scopeLimitations")
  private List<ScopeLimitation> scopeLimitations;

  @Nullable
  @Valid
  @Schema(name = "involvedChildren", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("involvedChildren")
  private List<InvolvedChild> involvedChildren;
}
