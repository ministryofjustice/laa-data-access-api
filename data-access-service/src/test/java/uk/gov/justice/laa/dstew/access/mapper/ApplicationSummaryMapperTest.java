package uk.gov.justice.laa.dstew.access.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class ApplicationSummaryMapperTest {

    @InjectMocks
    private ApplicationSummaryMapper applicationMapper = new ApplicationSummaryMapperImpl();

    @Test
    void shouldMapApplicationSummaryEntityToApplicationSummary() {
        UUID id = UUID.randomUUID();
        ApplicationSummaryEntity entity = new ApplicationSummaryEntity();
        entity.setId(id);
        entity.setCreatedAt(Instant.now());
        entity.setModifiedAt(Instant.now());
        entity.setApplicationReference("ref1");
        StatusCodeLookupEntity statusCodeLookupEntity = new StatusCodeLookupEntity();
        statusCodeLookupEntity.setCode("code1");
        statusCodeLookupEntity.setDescription("description1");
        statusCodeLookupEntity.setId(UUID.randomUUID());
        entity.setStatusCodeLookupEntity(statusCodeLookupEntity);

        ApplicationSummary result = applicationMapper.toApplicationSummary(entity);

        assertThat(result).isNotNull();
        assertThat(result.getApplicationId()).isEqualTo(id);
        assertThat(result.getApplicationReference()).isEqualTo("ref1");
        assertThat(result.getApplicationStatus()).isEqualTo(statusCodeLookupEntity.getCode());
        //assertThat(result.getCreatedAt()).isEqualTo(entity.getCreatedAt());
        //assertThat(result.getModifiedAt()).isEqualTo(entity.getModifiedAt());

    }
}
