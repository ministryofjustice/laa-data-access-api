package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.LinkedIndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Individual;

class LinkedIndividualMapperTest {

  private LinkedIndividualMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new LinkedIndividualMapper() {};
  }

  @Test
  void toIndividual_shouldReturnNull_whenEntityIsNull() {
    Individual result = mapper.toIndividual(null);
    assertThat(result).isNull();
  }

  @Test
  void toIndividual_shouldReturnNull_whenLinkedIndividualIsNull() {
    LinkedIndividualEntity entity = new LinkedIndividualEntity();
    entity.setLinkedIndividual(null);
    Individual result = mapper.toIndividual(entity);
    assertThat(result).isNull();
  }

  @Test
  void toIndividual_shouldMapFieldsCorrectly() {
    IndividualEntity linked = new IndividualEntity();
    linked.setId(UUID.randomUUID());
    linked.setFirstName("John");
    linked.setLastName("Doe");
    linked.setDateOfBirth(LocalDate.of(1980, 5, 15));
    linked.setIndividualContent(Map.of("key", "value"));

    LinkedIndividualEntity entity = new LinkedIndividualEntity();
    entity.setId(UUID.randomUUID());
    entity.setLinkedIndividual(linked);
    Individual result = mapper.toIndividual(entity);

    assertThat(result).isNotNull();
    assertThat(result.getFirstName()).isEqualTo("John");
    assertThat(result.getLastName()).isEqualTo("Doe");
    assertThat(result.getDateOfBirth()).isEqualTo(LocalDate.of(1980, 5, 15));
    assertThat(result.getDetails()).isEqualTo(Map.of("key", "value"));
  }
}
