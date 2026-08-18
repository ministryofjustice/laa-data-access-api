package uk.gov.justice.laa.dstew.access.applicationcontent;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Client address. Matches schema/common/Address.json. */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@ExcludeFromGeneratedCodeCoverage
public class ApplicationAddress implements Serializable {

  @Nullable private String addressLineOne;
  @Nullable private String addressLineTwo;
  @Nullable private String addressLineThree;
  @Nullable private String city;
  @Nullable private String county;
  @Nullable private String postcode;
  @Nullable private String organisation;
  @Nullable private String buildingNumberName;
  @Nullable private String countryCode;
  @Nullable private String countryName;
  @Nullable private String location;
  @Nullable private Boolean lookupUsed;
  @Nullable private String careOf;
  @Nullable private String careOfFirstName;
  @Nullable private String careOfLastName;
  @Nullable private String careOfOrganisationName;

  @JsonAnyGetter private Map<String, Object> additionalAddressData;

  /** Stores an additional unmapped address property. */
  @JsonAnySetter
  public ApplicationAddress putAdditionalProperty(String key, Object value) {
    if (this.additionalAddressData == null) {
      this.additionalAddressData = new HashMap<>();
    }
    this.additionalAddressData.put(key, value);
    return this;
  }
}
