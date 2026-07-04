package uk.gov.justice.laa.dstew.access.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

/** Class that runs validations for Access. */
@Component
@RequiredArgsConstructor
public class ApplicationValidations {

  private final EffectiveAuthorizationProvider entra;

  /** One by Validates an incoming PATCH. */
  public void checkApplicationUpdateRequest(final ApplicationUpdateRequest dto) {
    ValidationErrors validationErrors =
        ValidationErrors.empty()
            .addIf(dto == null, "ApplicationUpdateRequest and its content cannot be null")
            .addIf(
                dto != null && dto.getApplicationContent() == null,
                "Application content cannot be null")
            .addIf(
                dto != null
                    && dto.getApplicationContent() != null
                    && dto.getApplicationContent().isEmpty(),
                "Application content cannot be empty");
    if (!validationErrors.errors().isEmpty()) {
      throw new ValidationException(validationErrors.errors().stream().distinct().toList());
    }
  }
}
