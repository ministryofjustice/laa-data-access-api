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
import org.springframework.lang.Nullable;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * RequestApplicationContent.
 * Using the same format as the OpenAPI generator to enable switch when schema stable
 */
@ExcludeFromGeneratedCodeCoverage
public class RequestApplicationContent implements Serializable {

  private static final long serialVersionUID = 1L;

  private @Nullable ApplicationStatus status;

  private @Nullable String applicationReference;

  private ApplicationContent applicationContent;

  public RequestApplicationContent() {
    super();
  }

  /**
   * Constructor with only required parameters.
   */
  public RequestApplicationContent(ApplicationContent applicationContent) {
    this.applicationContent = applicationContent;
  }

  public RequestApplicationContent status(ApplicationStatus status) {
    this.status = status;
    return this;
  }

  /**
   * Get status.
   *
   * @return status
   */
  @Valid
  @Schema(name = "status", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  public ApplicationStatus getStatus() {
    return status;
  }

  public void setStatus(ApplicationStatus status) {
    this.status = status;
  }

  public RequestApplicationContent applicationReference(String applicationReference) {
    this.applicationReference = applicationReference;
    return this;
  }

  /**
   * Get applicationReference.
   *
   * @return applicationReference
   */

  @Schema(name = "applicationReference", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("applicationReference")
  public String getApplicationReference() {
    return applicationReference;
  }

  public void setApplicationReference(String applicationReference) {
    this.applicationReference = applicationReference;
  }

  public RequestApplicationContent applicationContent(ApplicationContent applicationContent) {
    this.applicationContent = applicationContent;
    return this;
  }

  /**
   * Get applicationContent.
   *
   * @return applicationContent
   */
  @NotNull
  @Valid
  @Schema(name = "applicationContent", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("applicationContent")
  public ApplicationContent getApplicationContent() {
    return applicationContent;
  }

  public void setApplicationContent(ApplicationContent applicationContent) {
    this.applicationContent = applicationContent;
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
  public RequestApplicationContent putAdditionalProperty(String key, Object value) {
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
    RequestApplicationContent requestApplicationContent = (RequestApplicationContent) o;
    return Objects.equals(this.status, requestApplicationContent.status)
        && Objects.equals(this.applicationReference, requestApplicationContent.applicationReference)
        && Objects.equals(this.applicationContent, requestApplicationContent.applicationContent)
        && Objects.equals(this.additionalProperties, requestApplicationContent.additionalProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, applicationReference, applicationContent, additionalProperties);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class RequestApplicationContent {\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    applicationReference: ").append(toIndentedString(applicationReference)).append("\n");
    sb.append("    applicationContent: ").append(toIndentedString(applicationContent)).append("\n");

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
   * A builder for RequestApplicationContent instances.
   */
  @ExcludeFromGeneratedCodeCoverage
  public static class Builder {

    private RequestApplicationContent instance;

    public Builder() {
      this(new RequestApplicationContent());
    }

    protected Builder(RequestApplicationContent instance) {
      this.instance = instance;
    }

    protected Builder copyOf(RequestApplicationContent value) {
      this.instance.setStatus(value.status);
      this.instance.setApplicationReference(value.applicationReference);
      this.instance.setApplicationContent(value.applicationContent);
      return this;
    }

    public Builder status(ApplicationStatus status) {
      this.instance.status(status);
      return this;
    }

    public Builder applicationReference(String applicationReference) {
      this.instance.applicationReference(applicationReference);
      return this;
    }

    public Builder applicationContent(ApplicationContent applicationContent) {
      this.instance.applicationContent(applicationContent);
      return this;
    }

    public Builder additionalProperties(Map<String, Object> additionalProperties) {
      this.instance.additionalProperties = additionalProperties;
      return this;
    }

    /**
     * returns a built RequestApplicationContent instance.
     * The builder is not reusable (NullPointerException)
     */
    public RequestApplicationContent build() {
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

