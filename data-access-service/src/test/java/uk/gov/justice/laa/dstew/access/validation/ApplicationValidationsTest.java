package uk.gov.justice.laa.dstew.access.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

@ExtendWith(MockitoExtension.class)
public class ApplicationValidationsTest {

  @Mock
  EffectiveAuthorizationProvider mockEntra;

  @InjectMocks
  ApplicationValidations classUnderTest;

  @Test
  void shouldNotThrowCreateRequestValidationErrorWhenContentIsValid() {
    ApplicationCreateRequest request = new ApplicationCreateRequest();
    request.setApplicationContent(new HashMap<>());

    assertDoesNotThrow(() -> classUnderTest.checkApplicationCreateRequest(request));
  }

  @Test
  void shouldThrowCreateRequestValidationErrorWhenContentIsNull() {
    ApplicationCreateRequest request = new ApplicationCreateRequest();
    request.setApplicationContent(null);

    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationCreateRequest(request));
  }

  @Test
  void shouldNotThrowUpdateRequestValidationErrorWhenContentIsValid() {
    ApplicationUpdateRequest request = new ApplicationUpdateRequest();
    request.setApplicationContent(new HashMap<>());

    assertDoesNotThrow(() -> classUnderTest.checkApplicationUpdateRequest(request, null));
  }

  @Test
  void shouldThrowUpdateRequestValidationErrorWhenContentIsNull() {
    ApplicationUpdateRequest request = new ApplicationUpdateRequest();
    request.setApplicationContent(null);

    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationUpdateRequest(request, null));
  }
}
