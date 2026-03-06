package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;

public class CaseworkerMapperTest {

    private final CaseworkerMapper mapper = Mappers.getMapper(CaseworkerMapper.class);

    @Test
    void givenCaseworkerEntity_whenToCaseworker_thenMapsFieldsCorrectly() {
        UUID id = UUID.randomUUID();
        String username = "caseworker1";

        CaseworkerEntity expectedCaseworkerEntity = CaseworkerEntity.builder()
                .id(id)
                .username(username)
                .build();

        var actualCaseworker = mapper.toCaseworker(expectedCaseworkerEntity);

        assertThat(actualCaseworker.getId()).isEqualTo(id);
        assertThat(actualCaseworker.getUsername()).isEqualTo(username);
    }

    @Test
    void givenNullCaseworker_whenToCaseworker_thenReturnNull() {
        CaseworkerEntity entity = null;
        assertThat(mapper.toCaseworker(entity)).isNull();
    }
}