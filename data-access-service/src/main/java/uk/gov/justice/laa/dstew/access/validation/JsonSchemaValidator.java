package uk.gov.justice.laa.dstew.access.validation;


import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

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
    JsonNode jsonNode = tools.jackson.databind.json.JsonMapper.builder()
        .build()
        .valueToTree(payload);

    String schemaPath = "schema/" + schemaVersion + "/" + schemaName;
    AbsoluteIri iri = new AbsoluteIri("classpath:" + schemaPath);
    SchemaLocation schemaLocation = new SchemaLocation(iri);
    Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7)
        .getSchema(schemaLocation);

    List<Error> validate = schema.validate(jsonNode);
    if (!validate.isEmpty()) {
      Set<String> errorMessages = validate.stream()
          .map(Error::getMessage)
          .collect(java.util.stream.Collectors.toSet());
      throw new ValidationException(errorMessages.stream().toList());
    }
  }
}
