package uk.gov.justice.laa.dstew.access.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

@ExtendWith(MockitoExtension.class)
public class ApplicationValidationsTest {

  @Mock
  EffectiveAuthorizationProvider mockEntra;

  @InjectMocks
  ApplicationValidations classUnderTest;

  // --- ApplicationCreateRequest tests ---

  @Test
  void shouldThrowCreateRequestValidationErrorWhenRequestIsNull() {
    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationCreateRequest(null));
  }

  @Test
  void shouldThrowCreateRequestValidationErrorWhenContentIsNull() {
    ApplicationCreateRequest request = new ApplicationCreateRequest();
    request.setApplicationContent(null);

    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationCreateRequest(request));
  }

  @Test
  void shouldThrowCreateRequestValidationErrorWhenContentIsEmpty() {
    ApplicationCreateRequest request = new ApplicationCreateRequest();
    request.setApplicationContent(new HashMap<>());

    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationCreateRequest(request));
  }

  @Test
  void shouldNotThrowCreateRequestValidationErrorWhenContentIsValid() {
    ApplicationCreateRequest request = new ApplicationCreateRequest();
    request.setStatus(ApplicationStatus.SUBMITTED);
    Map<String, Object> content = new HashMap<>();
    content.put("foo", "bar");
    request.setApplicationContent(content);

    assertDoesNotThrow(() -> classUnderTest.checkApplicationCreateRequest(request));
  }

  // --- ApplicationUpdateRequest tests ---

  @Test
  void shouldThrowUpdateRequestValidationErrorWhenRequestIsNull() {
    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationUpdateRequest(null, null));
  }

  @Test
  void shouldThrowUpdateRequestValidationErrorWhenContentIsNull() {
    ApplicationUpdateRequest request = new ApplicationUpdateRequest();
    request.setApplicationContent(null);

    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationUpdateRequest(request, null));
  }

  @Test
  void shouldThrowUpdateRequestValidationErrorWhenContentIsEmpty() {
    ApplicationUpdateRequest request = new ApplicationUpdateRequest();
    request.setApplicationContent(new HashMap<>());

    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationUpdateRequest(request, null));
  }

  @Test
  void shouldThrowUpdateRequestValidationErrorWhenProviderCannotUpdate() {
    when(mockEntra.hasAppRole("Provider")).thenReturn(true);
    when(mockEntra.hasAnyAppRole("Caseworker", "Administrator")).thenReturn(false);

    ApplicationUpdateRequest request = new ApplicationUpdateRequest();
    request.setStatus(ApplicationStatus.SUBMITTED);
    Map<String, Object> content = new HashMap<>();
    content.put("foo", "bar");
    request.setApplicationContent(content);

    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationUpdateRequest(request, null));
  }

  @Test
  void shouldNotThrowUpdateRequestValidationErrorWhenProviderCanUpdate() {
    when(mockEntra.hasAppRole("Provider")).thenReturn(true);
    when(mockEntra.hasAnyAppRole("Caseworker", "Administrator")).thenReturn(true);

    ApplicationUpdateRequest request = new ApplicationUpdateRequest();
    request.setStatus(ApplicationStatus.SUBMITTED);
    Map<String, Object> content = new HashMap<>();
    content.put("foo", "bar");
    request.setApplicationContent(content);

    assertDoesNotThrow(() -> classUnderTest.checkApplicationUpdateRequest(request, null));
  }

  @Test
  void shouldNotThrowUpdateRequestValidationErrorWhenNotProvider() {
    when(mockEntra.hasAppRole("Provider")).thenReturn(false);

    ApplicationUpdateRequest request = new ApplicationUpdateRequest();
    request.setStatus(ApplicationStatus.IN_PROGRESS);
    Map<String, Object> content = new HashMap<>();
    content.put("foo", "bar");
    request.setApplicationContent(content);

    assertDoesNotThrow(() -> classUnderTest.checkApplicationUpdateRequest(request, null));
  }

  // --- ValidationUtils tests ---

  @Test
  void notNullBooleanShouldReturnFalseWhenNull() {
    assert !ValidationUtils.notNull((Boolean) null);
  }

  @Test
  void notNullBooleanShouldReturnTrueWhenTrue() {
    assert ValidationUtils.notNull(true);
  }

  @Test
  void notNullStringShouldReturnEmptyStringWhenNull() {
    assert ValidationUtils.notNull((String) null).equals("");
  }

  @Test
  void notNullStringShouldReturnSameStringWhenNotNull() {
    assert ValidationUtils.notNull("hello").equals("hello");
  }
}
