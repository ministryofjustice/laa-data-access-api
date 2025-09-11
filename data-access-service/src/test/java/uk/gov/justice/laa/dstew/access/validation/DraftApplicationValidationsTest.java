package uk.gov.justice.laa.dstew.access.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.model.DraftApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.DraftApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DraftApplicationValidationsTest {

    @InjectMocks DraftApplicationValidations classUnderTest;
    @Mock EffectiveAuthorizationProvider mockEntra;

    @Test
    void shouldValidateCheckCreateRequest() {
        DraftApplicationCreateRequest request = new DraftApplicationCreateRequest();
        request.setProviderId(UUID.randomUUID());
        assertDoesNotThrow(() -> classUnderTest.checkCreateRequest(request));
    }

    @Test
    void shouldNotThrowUpdateRequestValidationErrorWhenClientIdIsNull() {
        when(mockEntra.hasAppRole("Provider")).thenReturn(true);
        when(mockEntra.hasAnyAppRole("Caseworker", "Administrator")).thenReturn(false);

        DraftApplicationUpdateRequest request = new DraftApplicationUpdateRequest();
        request.setClientId(null);

        assertDoesNotThrow(() -> classUnderTest.checkDraftApplicationUpdateRequest(request, null));
    }

    @Test
    void shouldThrowUpdateRequestValidationErrorWhenClientIdIsNotNull() {
        when(mockEntra.hasAppRole("Provider")).thenReturn(true);
        when(mockEntra.hasAnyAppRole("Caseworker", "Administrator")).thenReturn(false);

        DraftApplicationUpdateRequest request = new DraftApplicationUpdateRequest();
        request.setClientId(UUID.randomUUID());

        assertThrows(ValidationException.class,
                () -> classUnderTest.checkDraftApplicationUpdateRequest(request, null));
    }

    @Test
    void shouldNotThrowUpdateRequestValidationErrorWhenAppRoleIsNotProvider() {
        when(mockEntra.hasAppRole("Provider")).thenReturn(false);
        DraftApplicationUpdateRequest request = new DraftApplicationUpdateRequest();
        request.setClientId(null);
        assertDoesNotThrow(() -> classUnderTest.checkDraftApplicationUpdateRequest(request, null));
    }

    @Test
    void shouldNotThrowUpdateRequestValidationErrorWhenCaseWorkerOrAdministrator() {
        when(mockEntra.hasAppRole("Provider")).thenReturn(true);
        when(mockEntra.hasAnyAppRole("Caseworker", "Administrator")).thenReturn(true);
        DraftApplicationUpdateRequest request = new DraftApplicationUpdateRequest();
        request.setClientId(UUID.randomUUID());
        assertDoesNotThrow(() -> classUnderTest.checkDraftApplicationUpdateRequest(request, null));
    }
}
