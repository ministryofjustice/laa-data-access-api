package uk.gov.justice.laa.dstew.access.transformation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

/**
 * Provide role-based transformation to the Application responses.
 */
@Component
@RequiredArgsConstructor
class ApplicationTransformer implements ResponseTransformer<Application> {
  private final EffectiveAuthorizationProvider entra;

  @Override
  public Application transform(final Application response) {
    if (!entra.hasAppRole("ProceedingReader")) {
      // response.setProceedings(null);
    }
    return response;
  }
}
