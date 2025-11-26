package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;

public class CaseworkerMapperTest {
  private CaseworkerMapper mapper = new CaseworkerMapperImpl();

  @Test
  void toCaseworkerMapsFieldsCorrectly() {
    CaseworkerEntity caseworker = CaseworkerEntity.builder()
                                                  .id(UUID.randomUUID())
                                                  .username("caseworker1")
                                                  .build();
    var result = mapper.toCaseworker(caseworker);

    assertThat(result.getId()).isEqualTo(caseworker.getId());
    assertThat(result.getUsername()).isEqualTo(caseworker.getUsername());
  }

  @Test
  void toCaseworkerMapsNullToNull() {
    assertThat(mapper.toCaseworker(null)).isNull();
  }
}
