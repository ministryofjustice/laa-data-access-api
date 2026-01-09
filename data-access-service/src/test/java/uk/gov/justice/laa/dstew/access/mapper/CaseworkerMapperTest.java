package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;

public class CaseworkerMapperTest {
  private CaseworkerMapper mapper = new CaseworkerMapperImpl();

  @Test
  void givenCaseworkerEntity_whenToCaseworker_thenMapsFieldsCorrectly() {
    CaseworkerEntity expectedCaseworkerEntity = CaseworkerEntity.builder()
                                                  .id(UUID.randomUUID())
                                                  .username("caseworker1")
                                                  .build();
    var actualCaseworker = mapper.toCaseworker(expectedCaseworkerEntity);

    assertThat(actualCaseworker.getId()).isEqualTo(expectedCaseworkerEntity.getId());
    assertThat(actualCaseworker.getUsername()).isEqualTo(expectedCaseworkerEntity.getUsername());
  }

  @Test
  void givenNullCaseworker_whenToCaseworker_thenReturnNull() {

      assertThat(mapper.toCaseworker(null)).isNull();
  }
}
