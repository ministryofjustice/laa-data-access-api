package uk.gov.justice.laa.dstew.access.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApplicationValidationsTest {

    @Mock
    EffectiveAuthorizationProvider mockEntra;

    @InjectMocks
    ApplicationValidations classUnderTest;

    @Test
    void shouldNotThrowUpdateRequestValidationErrorWhenClientIdIsNull() {
        when(mockEntra.hasAppRole("Provider")).thenReturn(true);
        when(mockEntra.hasAnyAppRole("Caseworker", "Administrator")).thenReturn(false);

        ApplicationUpdateRequest request = new ApplicationUpdateRequest();
        request.setClientId(null);

        assertDoesNotThrow(() -> classUnderTest.checkApplicationUpdateRequest(request, null));
    }

    @Test
    void shouldThrowUpdateRequestValidationErrorWhenClientIdIsNotNull() {
        when(mockEntra.hasAppRole("Provider")).thenReturn(true);
        when(mockEntra.hasAnyAppRole("Caseworker", "Administrator")).thenReturn(false);

        ApplicationUpdateRequest request = new ApplicationUpdateRequest();
        request.setClientId(UUID.randomUUID());

        assertThrows(ValidationException.class,
                () -> classUnderTest.checkApplicationUpdateRequest(request, null));
    }

    @Test
    void shouldNotThrowUpdateRequestValidationErrorWhenAppRoleIsNotProvider() {
        when(mockEntra.hasAppRole("Provider")).thenReturn(false);
        ApplicationUpdateRequest request = new ApplicationUpdateRequest();
        request.setClientId(null);
        assertDoesNotThrow(() -> classUnderTest.checkApplicationUpdateRequest(request, null));
    }

    @Test
    void shouldNotThrowUpdateRequestValidationErrorWhenCaseWorkerOrAdministrator() {
        when(mockEntra.hasAppRole("Provider")).thenReturn(true);
        when(mockEntra.hasAnyAppRole("Caseworker", "Administrator")).thenReturn(true);
        ApplicationUpdateRequest request = new ApplicationUpdateRequest();
        request.setClientId(UUID.randomUUID());
        assertDoesNotThrow(() -> classUnderTest.checkApplicationUpdateRequest(request, null));
    }

    @Test
    void shouldThrowCreateRequestValidationErrorWhenAllConditionsSet() {
        ApplicationCreateRequest request = new ApplicationCreateRequest();
        request.setProviderOfficeId(null);
        request.setIsEmergencyApplication(false);
        request.setStatusCode("NEW");
        assertThrows(ValidationException.class,
                () -> classUnderTest.checkApplicationCreateRequest(request));
    }

    @Test
    void shouldNotThrowCreateRequestValidationErrorWhenProviderOfficeId() {
        ApplicationCreateRequest request = new ApplicationCreateRequest();
        request.setProviderOfficeId("Provider");
        request.setIsEmergencyApplication(false);
        request.setStatusCode("NEW");
        assertDoesNotThrow(() -> classUnderTest.checkApplicationCreateRequest(request));
    }

    @Test
    void shouldNotThrowCreateRequestValidationErrorWhenNotEmergencyApplication() {
        ApplicationCreateRequest request = new ApplicationCreateRequest();
        request.setProviderOfficeId(null);
        request.setIsEmergencyApplication(true);
        request.setStatusCode("NEW");
        assertDoesNotThrow(() -> classUnderTest.checkApplicationCreateRequest(request));
    }
}
