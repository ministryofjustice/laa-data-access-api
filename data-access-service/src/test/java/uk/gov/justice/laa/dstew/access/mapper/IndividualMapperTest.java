package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Individual;

class IndividualMapperTest {

  private IndividualMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new IndividualMapper() {};
  }

  @Test
  void toIndividual_shouldReturnNull_whenEntityIsNull() {
    assertThat(mapper.toIndividual(null)).isNull();
  }

  @Test
  void toIndividual_shouldMapFieldsCorrectly() {
    IndividualEntity entity = new IndividualEntity();
    entity.setId(UUID.randomUUID());
    entity.setFirstName("John");
    entity.setLastName("Doe");
    entity.setDateOfBirth(LocalDate.of(1980, 5, 15));
    entity.setIndividualContent(Map.of("key", "value"));

    Individual result = mapper.toIndividual(entity);

    assertThat(result).isNotNull();
    assertThat(result.getFirstName()).isEqualTo("John");
    assertThat(result.getLastName()).isEqualTo("Doe");
    assertThat(result.getDateOfBirth()).isEqualTo(LocalDate.of(1980, 5, 15));
    assertThat(result.getDetails()).isEqualTo(Map.of("key", "value"));
  }
}
