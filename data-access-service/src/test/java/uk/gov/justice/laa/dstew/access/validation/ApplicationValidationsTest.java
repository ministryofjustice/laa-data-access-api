package uk.gov.justice.laa.dstew.access.validation;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

@ExtendWith(MockitoExtension.class)
public class ApplicationValidationsTest {

  @Mock EffectiveAuthorizationProvider mockEntra;
  @Mock IndividualValidations individualValidator;

  @InjectMocks ApplicationValidations classUnderTest;
}
