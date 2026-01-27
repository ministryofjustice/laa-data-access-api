package uk.gov.justice.laa.dstew.access.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * Proceeding.
 * Using the same format as the OpenAPI generator to enable switch when schema stable
 */
public class Proceeding implements Serializable {

  private static final long serialVersionUID = 1L;

  private UUID id;

  private @Nullable String categoryOfLaw;

  private @Nullable String matterType;

  private @Nullable Boolean usedDelegatedFunctions;

  private Boolean leadProceeding;

  private String description;

  public Proceeding() {
    super();
  }

  /**
   * Constructor with only required parameters.
   */
  public Proceeding(UUID id, Boolean leadProceeding, String description) {
    this.id = id;
    this.leadProceeding = leadProceeding;
    this.description = description;
  }

  public Proceeding id(UUID id) {
    this.id = id;
    return this;
  }

  /**
   * Get id.
   *
   * @return id
   */
  @NotNull
  @Valid
  @Schema(name = "id", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("id")
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Proceeding categoryOfLaw(String categoryOfLaw) {
    this.categoryOfLaw = categoryOfLaw;
    return this;
  }

  /**
   * Get categoryOfLaw.
   *
   * @return categoryOfLaw
   */

  @Schema(name = "categoryOfLaw", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("categoryOfLaw")
  public String getCategoryOfLaw() {
    return categoryOfLaw;
  }

  public void setCategoryOfLaw(String categoryOfLaw) {
    this.categoryOfLaw = categoryOfLaw;
  }

  public Proceeding matterType(String matterType) {
    this.matterType = matterType;
    return this;
  }

  /**
   * Get matterType.
   *
   * @return matterType
   */

  @Schema(name = "matterType", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("matterType")
  public String getMatterType() {
    return matterType;
  }

  public void setMatterType(String matterType) {
    this.matterType = matterType;
  }

  public Proceeding usedDelegatedFunctions(Boolean usedDelegatedFunctions) {
    this.usedDelegatedFunctions = usedDelegatedFunctions;
    return this;
  }

  /**
   * Get usedDelegatedFunctions.
   *
   * @return usedDelegatedFunctions
   */

  @Schema(name = "usedDelegatedFunctions", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("usedDelegatedFunctions")
  public Boolean getUsedDelegatedFunctions() {
    return usedDelegatedFunctions;
  }

  public void setUsedDelegatedFunctions(Boolean usedDelegatedFunctions) {
    this.usedDelegatedFunctions = usedDelegatedFunctions;
  }

  public Proceeding leadProceeding(Boolean leadProceeding) {
    this.leadProceeding = leadProceeding;
    return this;
  }

  /**
   * Get leadProceeding.
   *
   * @return leadProceeding
   */
  @NotNull
  @Schema(name = "leadProceeding", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("leadProceeding")
  public Boolean getLeadProceeding() {
    return leadProceeding;
  }

  public void setLeadProceeding(Boolean leadProceeding) {
    this.leadProceeding = leadProceeding;
  }

  public Proceeding description(String description) {
    this.description = description;
    return this;
  }

  /**
   * Get description.
   *
   * @return description
   */
  @NotNull
  @Schema(name = "description", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("description")
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * A container for additional, undeclared properties.
   * This is a holder for any undeclared properties as specified with
   * the 'additionalProperties' keyword in the OAS document.
   */
  private Map<String, Object> additionalProperties;

  /**
   * Set the additional (undeclared) property with the specified name and value.
   * If the property does not already exist, create it otherwise replace it.
   */
  @JsonAnySetter
  public Proceeding putAdditionalProperty(String key, Object value) {
    if (this.additionalProperties == null) {
      this.additionalProperties = new HashMap<String, Object>();
    }
    this.additionalProperties.put(key, value);
    return this;
  }

  /**
   * Return the additional (undeclared) property.
   */
  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  /**
   * Return the additional (undeclared) property with the specified name.
   */
  public Object getAdditionalProperty(String key) {
    if (this.additionalProperties == null) {
      return null;
    }
    return this.additionalProperties.get(key);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Proceeding proceeding = (Proceeding) o;
    return Objects.equals(this.id, proceeding.id)
        && Objects.equals(this.categoryOfLaw, proceeding.categoryOfLaw)
        && Objects.equals(this.matterType, proceeding.matterType)
        && Objects.equals(this.usedDelegatedFunctions, proceeding.usedDelegatedFunctions)
        && Objects.equals(this.leadProceeding, proceeding.leadProceeding)
        && Objects.equals(this.description, proceeding.description)
        && Objects.equals(this.additionalProperties, proceeding.additionalProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, categoryOfLaw, matterType, usedDelegatedFunctions, leadProceeding, description,
        additionalProperties);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Proceeding {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    categoryOfLaw: ").append(toIndentedString(categoryOfLaw)).append("\n");
    sb.append("    matterType: ").append(toIndentedString(matterType)).append("\n");
    sb.append("    usedDelegatedFunctions: ").append(toIndentedString(usedDelegatedFunctions)).append("\n");
    sb.append("    leadProceeding: ").append(toIndentedString(leadProceeding)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");

    sb.append("    additionalProperties: ").append(toIndentedString(additionalProperties)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces.
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

  /**
   * Builder for Proceeding instances.
   */
  public static class Builder {

    private Proceeding instance;

    public Builder() {
      this(new Proceeding());
    }

    protected Builder(Proceeding instance) {
      this.instance = instance;
    }

    protected Builder copyOf(Proceeding value) {
      this.instance.setId(value.id);
      this.instance.setCategoryOfLaw(value.categoryOfLaw);
      this.instance.setMatterType(value.matterType);
      this.instance.setUsedDelegatedFunctions(value.usedDelegatedFunctions);
      this.instance.setLeadProceeding(value.leadProceeding);
      this.instance.setDescription(value.description);
      return this;
    }

    public Builder id(UUID id) {
      this.instance.id(id);
      return this;
    }

    public Builder categoryOfLaw(String categoryOfLaw) {
      this.instance.categoryOfLaw(categoryOfLaw);
      return this;
    }

    public Builder matterType(String matterType) {
      this.instance.matterType(matterType);
      return this;
    }

    public Builder usedDelegatedFunctions(Boolean usedDelegatedFunctions) {
      this.instance.usedDelegatedFunctions(usedDelegatedFunctions);
      return this;
    }

    public Builder leadProceeding(Boolean leadProceeding) {
      this.instance.leadProceeding(leadProceeding);
      return this;
    }

    public Builder description(String description) {
      this.instance.description(description);
      return this;
    }

    public Builder additionalProperties(Map<String, Object> additionalProperties) {
      this.instance.additionalProperties = additionalProperties;
      return this;
    }

    /**
     * returns a built Proceeding instance.
     * The builder is not reusable (NullPointerException)
     */
    public Proceeding build() {
      try {
        return this.instance;
      } finally {
        // ensure that this.instance is not reused
        this.instance = null;
      }
    }

    @Override
    public String toString() {
      return getClass() + "=(" + instance + ")";
    }
  }

  /**
   * Create a builder with no initialized field (except for the default values).
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Create a builder with a shallow copy of this instance.
   */
  public Builder toBuilder() {
    Builder builder = new Builder();
    return builder.copyOf(this);
  }

}

