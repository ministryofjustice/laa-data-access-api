package uk.gov.justice.laa.dstew.access.transformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

@ExtendWith(MockitoExtension.class)
public class ApplicationTransformerTest {

    @InjectMocks
    ApplicationTransformer classUnderTest;

    @Mock
    EffectiveAuthorizationProvider mockEntra;

    @Test
    void shouldTransformApplication() {
        Application request = Application.builder()
                .id(UUID.randomUUID())
                .build();

        when(mockEntra.hasAppRole("ProceedingReader")).thenReturn(true);

        Application response = classUnderTest.transform(request);
        assertThat(response.getId()).isEqualTo(request.getId());
    }
}