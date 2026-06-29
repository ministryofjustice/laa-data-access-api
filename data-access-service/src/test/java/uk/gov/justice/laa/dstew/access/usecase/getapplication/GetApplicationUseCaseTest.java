package uk.gov.justice.laa.dstew.access.usecase.getapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.dto.ApplicationDbProjection;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.infrastructure.GetApplicationApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.getapplication.ApplicationDbProjectionGenerator;

@ExtendWith(MockitoExtension.class)
class GetApplicationUseCaseTest {

  @Mock private GetApplicationApplicationGateway applicationGateway;

  private GetApplicationUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetApplicationUseCase(applicationGateway, new GetApplicationReadModelMapper());
  }

  @Test
  void givenExistingApplicationId_whenExecuted_thenReturnsApplicationReadModel() {
    UUID id = UUID.randomUUID();
    ApplicationDbProjection projection =
        DataGenerator.createDefault(
            ApplicationDbProjectionGenerator.class, builder -> builder.id(id));

    when(applicationGateway.findApplicationById(id)).thenReturn(Optional.of(projection));

    ApplicationReadModel actual = useCase.execute(id);

    assertThat(actual.id()).isEqualTo(projection.id());
    assertThat(actual.status()).isEqualTo(projection.status());
    assertThat(actual.applicationType()).isEqualTo(GetApplicationUseCase.APPLICATION_TYPE_INITIAL);
    assertThat(actual.provider()).isNotNull();
    assertThat(actual.provider().officeCode()).isEqualTo(projection.officeCode());
    assertThat(actual.provider().contactEmail()).isEqualTo(projection.submitterEmail());
    verify(applicationGateway, times(1)).findApplicationById(id);
  }

  @ParameterizedTest(name = "[{index}] officeCode={0}, submitterEmail={1}")
  @MethodSource("providerScenarios")
  void givenProviderFieldVariants_whenExecuted_thenProviderIsMappedAsExpected(
      String officeCode,
      String submitterEmail,
      boolean providerExpected,
      String expectedOfficeCode,
      String expectedContactEmail) {
    UUID id = UUID.randomUUID();
    ApplicationDbProjection projection =
        DataGenerator.createDefault(
            ApplicationDbProjectionGenerator.class,
            builder -> builder.id(id).officeCode(officeCode).submitterEmail(submitterEmail));

    when(applicationGateway.findApplicationById(id)).thenReturn(Optional.of(projection));

    ApplicationReadModel actual = useCase.execute(id);

    if (!providerExpected) {
      assertThat(actual.provider()).isNull();
    } else {
      assertThat(actual.provider()).isNotNull();
      assertThat(actual.provider().officeCode()).isEqualTo(expectedOfficeCode);
      assertThat(actual.provider().contactEmail()).isEqualTo(expectedContactEmail);
    }

    verify(applicationGateway, times(1)).findApplicationById(id);
  }

  @Test
  void givenNonExistentApplicationId_whenExecuted_thenThrowsResourceNotFoundException() {
    UUID id = UUID.randomUUID();

    when(applicationGateway.findApplicationById(id)).thenReturn(Optional.empty());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(id))
        .withMessageContaining("No application found with id: " + id);

    verify(applicationGateway, times(1)).findApplicationById(id);
  }

  private static Stream<Arguments> providerScenarios() {
    return Stream.of(
        arguments(null, null, false, null, null),
        arguments(null, "x@y.z", true, null, "x@y.z"),
        arguments("OFFICE1", null, true, "OFFICE1", null));
  }
}
