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
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.dto.ApplicationDbProjection;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.infrastructure.GetApplicationApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.getapplication.ApplicationDbProjectionGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.getapplication.ApplicationReadModelGenerator;

@ExtendWith(MockitoExtension.class)
class GetApplicationUseCaseTest {

  @Mock private GetApplicationApplicationGateway applicationGateway;
  @Mock private GetApplicationReadModelMapper readModelMapper;

  private GetApplicationUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetApplicationUseCase(applicationGateway, readModelMapper);
  }

  @Test
  void givenExistingApplicationId_whenExecuted_thenReturnsApplicationReadModel() {
    UUID id = UUID.randomUUID();
    ApplicationDbProjection projection =
        DataGenerator.createDefault(
            ApplicationDbProjectionGenerator.class, builder -> builder.id(id));
    ApplicationReadModel expected =
        DataGenerator.createDefault(ApplicationReadModelGenerator.class, builder -> builder.id(id));

    when(applicationGateway.findApplicationById(id)).thenReturn(Optional.of(projection));
    when(readModelMapper.toApplicationReadModel(projection)).thenReturn(expected);

    ApplicationReadModel actual = useCase.execute(id);

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    verify(applicationGateway, times(1)).findApplicationById(id);
    verify(readModelMapper, times(1)).toApplicationReadModel(projection);
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
}
