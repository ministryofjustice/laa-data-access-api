package uk.gov.justice.laa.dstew.access.usecase.getapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.domain.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.infrastructure.GetApplicationApplicationGateway;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.getapplication.ApplicationReadModelGenerator;

@ExtendWith(MockitoExtension.class)
class GetApplicationUseCaseTest {

  @Mock private GetApplicationApplicationGateway applicationGateway;

  private GetApplicationUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetApplicationUseCase(applicationGateway);
  }

  @Test
  void givenExistingApplicationId_whenExecuted_thenReturnsApplicationReadModel() {
    UUID id = UUID.randomUUID();
    ApplicationReadModel expected =
        DataGenerator.createDefault(ApplicationReadModelGenerator.class, builder -> builder.id(id));

    when(applicationGateway.findApplicationReadModelById(id)).thenReturn(Optional.of(expected));

    ApplicationReadModel actual = useCase.execute(id);

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    verify(applicationGateway, times(1)).findApplicationReadModelById(id);
  }

  @Test
  void givenNonExistentApplicationId_whenExecuted_thenThrowsResourceNotFoundException() {
    UUID id = UUID.randomUUID();

    when(applicationGateway.findApplicationReadModelById(id)).thenReturn(Optional.empty());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(id))
        .withMessageContaining("No application found with id: " + id);

    verify(applicationGateway, times(1)).findApplicationReadModelById(id);
  }
}
