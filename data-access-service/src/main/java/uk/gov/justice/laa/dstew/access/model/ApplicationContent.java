package uk.gov.justice.laa.dstew.access.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.lang.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * ApplicationContent pojo.
 * Using the same format as the OpenAPI generator to enable switch when schema stable
 */
@ExcludeFromGeneratedCodeCoverage
public class ApplicationContent implements Serializable {

  private static final long serialVersionUID = 1L;

  private UUID id;

  @Valid
  private List<Proceeding> proceedings = new ArrayList<>();

  private String submittedAt;

  private @Nullable String applicationRef;

  public ApplicationContent() {
    super();
  }

  /**
   * Constructor with only required parameters.
   */
  public ApplicationContent(UUID id, List<Proceeding> proceedings, String submittedAt) {
    this.id = id;
    this.proceedings = proceedings;
    this.submittedAt = submittedAt;
  }

  public ApplicationContent id(UUID id) {
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

  public ApplicationContent proceedings(List<Proceeding> proceedings) {
    this.proceedings = proceedings;
    return this;
  }

  /**
   * Add item to proceedings.
   *
   * @return this
   */
  public ApplicationContent addProceedingsItem(Proceeding proceedingsItem) {
    if (this.proceedings == null) {
      this.proceedings = new ArrayList<>();
    }
    this.proceedings.add(proceedingsItem);
    return this;
  }

  /**
   * Get proceedings.
   *
   * @return proceedings
   */
  @NotNull
  @Valid
  @Size(min = 1)
  @Schema(name = "proceedings", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("proceedings")
  public List<Proceeding> getProceedings() {
    return proceedings;
  }

  public void setProceedings(List<Proceeding> proceedings) {
    this.proceedings = proceedings;
  }

  public ApplicationContent submittedAt(String submittedAt) {
    this.submittedAt = submittedAt;
    return this;
  }

  /**
   * Get submittedAt.
   *
   * @return submittedAt
   */
  @NotNull
  @Schema(name = "submittedAt", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("submittedAt")
  public String getSubmittedAt() {
    return submittedAt;
  }

  public void setSubmittedAt(String submittedAt) {
    this.submittedAt = submittedAt;
  }

  public ApplicationContent applicationRef(String applicationRef) {
    this.applicationRef = applicationRef;
    return this;
  }

  /**
   * Get applicationRef.
   *
   * @return applicationRef
   */

  @Schema(name = "applicationRef", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("applicationRef")
  public String getApplicationRef() {
    return applicationRef;
  }

  public void setApplicationRef(String applicationRef) {
    this.applicationRef = applicationRef;
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
  public ApplicationContent putAdditionalProperty(String key, Object value) {
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
    ApplicationContent applicationContent = (ApplicationContent) o;
    return Objects.equals(this.id, applicationContent.id)
        && Objects.equals(this.proceedings, applicationContent.proceedings)
        && Objects.equals(this.submittedAt, applicationContent.submittedAt)
        && Objects.equals(this.applicationRef, applicationContent.applicationRef)
        && Objects.equals(this.additionalProperties, applicationContent.additionalProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, proceedings, submittedAt, applicationRef, additionalProperties);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ApplicationContent {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    proceedings: ").append(toIndentedString(proceedings)).append("\n");
    sb.append("    submittedAt: ").append(toIndentedString(submittedAt)).append("\n");
    sb.append("    applicationRef: ").append(toIndentedString(applicationRef)).append("\n");

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
   * A builder for ApplicationContent instances.
   */
  @ExcludeFromGeneratedCodeCoverage
  public static class Builder {

    private ApplicationContent instance;

    public Builder() {
      this(new ApplicationContent());
    }

    protected Builder(ApplicationContent instance) {
      this.instance = instance;
    }

    protected Builder copyOf(ApplicationContent value) {
      this.instance.setId(value.id);
      this.instance.setProceedings(value.proceedings);
      this.instance.setSubmittedAt(value.submittedAt);
      this.instance.setApplicationRef(value.applicationRef);
      return this;
    }

    public Builder id(UUID id) {
      this.instance.id(id);
      return this;
    }

    public Builder proceedings(List<Proceeding> proceedings) {
      this.instance.proceedings(proceedings);
      return this;
    }

    public Builder submittedAt(String submittedAt) {
      this.instance.submittedAt(submittedAt);
      return this;
    }

    public Builder applicationRef(String applicationRef) {
      this.instance.applicationRef(applicationRef);
      return this;
    }

    public Builder additionalProperties(Map<String, Object> additionalProperties) {
      this.instance.additionalProperties = additionalProperties;
      return this;
    }

    /**
     * returns a built ApplicationContent instance.
     * The builder is not reusable (NullPointerException)
     */
    public ApplicationContent build() {
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

