package uk.gov.justice.laa.dstew.access.validation;


import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.Error;
import com.networknt.schema.ExecutionContext;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates payloads against versioned JSON Schemas loaded from the classpath.
 * Schemas are expected at {@code schema/{version}/{schemaName}}, e.g.
 * {@code schema/1/ApplyApplication.json}.
 */
@Component
@RequiredArgsConstructor
public class JsonSchemaValidator {


  /**
   * Validate a payload against a named schema at the given version.
   *
   * @param payload       the object to validate (e.g. {@code Map<String, Object>})
   * @param schemaName    the schema filename, e.g. {@code "ApplyApplication.json"}
   * @param schemaVersion the version directory, e.g. {@code 1} loads from {@code schema/1/}
   * @throws ValidationException if the payload does not conform to the schema
   */
  public void validate(Object payload, String schemaName, int schemaVersion) {
    JsonNode jsonNode = JsonMapper.builder()
        .build()
        .valueToTree(payload);

    Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7)
        .getSchema(getSchemaLocation(schemaName, schemaVersion));

    List<Error> validate = schema.validate(String.valueOf(jsonNode), InputFormat.JSON,
        getExecutionCustomizer());
    if (!validate.isEmpty()) {
      List<String> errorMessages = validate.stream()
          .map(Error::toString)
          .map(JsonSchemaValidator::formatError)
          .toList();
      throw new ValidationException(errorMessages);
    }
  }

  private static @NonNull Consumer<ExecutionContext> getExecutionCustomizer() {
    // Enable format assertions to validate UUIDs, date-times, etc. as per the schema
    return context -> context.executionConfig(
        config -> config.formatAssertionsEnabled(true)
    );
  }

  private static @NonNull SchemaLocation getSchemaLocation(String schemaName, int schemaVersion) {
    String schemaPath = "schema/" + schemaVersion + "/" + schemaName;
    AbsoluteIri iri = new AbsoluteIri("classpath:" + schemaPath);
    return new SchemaLocation(iri);
  }

  private static String formatError(String error) {
    // Remove leading and trailing blank lines, remove initial / and : characters
    error = error.trim();
    return error.replaceAll("^[/:]+", ""); // Remove leading / and :
  }

}
