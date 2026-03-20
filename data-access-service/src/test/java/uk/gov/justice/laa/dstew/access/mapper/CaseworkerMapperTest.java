package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;

@ExtendWith(MockitoExtension.class)
public class CaseworkerMapperTest extends BaseMapperTest {

    @InjectMocks
    private CaseworkerMapperImpl mapper;

    @Test
    void givenNullCaseworker_whenToCaseworker_thenReturnNull() {
        assertThat(mapper.toCaseworker(null)).isNull();
    }

    @Test
    void givenCaseworkerEntity_whenToCaseworker_thenMapsFieldsCorrectly() {
        CaseworkerEntity entity = DataGenerator.createDefault(CaseworkerGenerator.class,
                builder -> builder.id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                                  .username("caseworker1"));

        var result = mapper.toCaseworker(entity);

        assertThat(result.getId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(result.getUsername()).isEqualTo("caseworker1");
    }

    @Test
    void givenCaseworkerEntityWithAllNullFields_whenToCaseworker_thenAllFieldsAreNull() {
        CaseworkerEntity entity = DataGenerator.createDefault(CaseworkerGenerator.class,
                builder -> builder.id(null).username(null));

        var result = mapper.toCaseworker(entity);

        assertThat(result.getId()).isNull();
        assertThat(result.getUsername()).isNull();
    }
}