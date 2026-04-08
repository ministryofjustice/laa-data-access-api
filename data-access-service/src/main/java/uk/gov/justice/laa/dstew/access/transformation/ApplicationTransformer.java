package uk.gov.justice.laa.dstew.access.transformation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

/**
 * Provide role-based transformation to the Application responses.
 */
@Component
@RequiredArgsConstructor
class ApplicationTransformer implements ResponseTransformer<ApplicationResponse> {
  private final EffectiveAuthorizationProvider entra;

  @Override
  public ApplicationResponse transform(final ApplicationResponse response) {
    if (!entra.hasAppRole("ProceedingReader")) {
      // response.setProceedings(null);
    }
    return response;
  }
}
