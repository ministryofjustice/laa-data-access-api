package uk.gov.justice.laa.dstew.access.mapper;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ApplicationProceedingEntity;
import uk.gov.justice.laa.dstew.access.entity.EmbeddedRecordHistoryEntity;

import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingCreateRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class ApplicationMapperTest {

    @InjectMocks
    private ApplicationMapper applicationMapper = new ApplicationMapperImpl();

    private EmbeddedRecordHistoryEntity createEmbeddedRecordHistoryEntity(
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy
    ) {
        EmbeddedRecordHistoryEntity entity = new EmbeddedRecordHistoryEntity();
        entity.setCreatedAt(createdAt);
        entity.setCreatedBy(createdBy);
        entity.setUpdatedAt(updatedAt);
        entity.setUpdatedBy(updatedBy);
        return entity;
    }

    private ApplicationProceedingEntity createApplicationProceedingEntity(
            UUID id,
            String levelServiceCode,
            String proceedingCode,
            EmbeddedRecordHistoryEntity recordHistory,
            ApplicationEntity applicationEntity
    ) {
        ApplicationProceedingEntity entity = new ApplicationProceedingEntity();
        entity.setId(id);
        entity.setApplication(applicationEntity);
        entity.setLevelOfServiceCode(levelServiceCode);
        entity.setProceedingCode(proceedingCode);
        entity.setRecordHistory(recordHistory);
        entity.setApplication(applicationEntity);
        return entity;
    }

    private ApplicationEntity createApplicationEntity(
            UUID id,
            UUID clientId,
            String firmId,
            String officeId,
            String statusCode,
            Boolean isEmergency,
            String statement,
            EmbeddedRecordHistoryEntity recordHistory,
            List<ApplicationProceedingEntity> proceedings
    ) {
        ApplicationEntity applicationEntity = new ApplicationEntity();
        applicationEntity.setId(id);
        applicationEntity.setClientId(clientId);
        applicationEntity.setProviderFirmId(firmId);
        applicationEntity.setProviderOfficeId(officeId);
        applicationEntity.setStatusCode(statusCode);
        applicationEntity.setIsEmergencyApplication(isEmergency);
        applicationEntity.setStatementOfCase(statement);
        applicationEntity.setRecordHistory(recordHistory);
        applicationEntity.setProceedings(proceedings);
        return applicationEntity;
    }

    @Test
    void shouldMapApplicationEntityToApplication() {

        UUID id = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        EmbeddedRecordHistoryEntity recordHistory =
                createEmbeddedRecordHistoryEntity(Instant.now(), "admin", Instant.now(), "admin");

        ApplicationEntity applicationEntity = createApplicationEntity(
            id,
            clientId,
                "firm-001",
                "office-001",
                "NEW",
                true,
                "statementofcase",
                recordHistory,
                List.of(
                    createApplicationProceedingEntity(
                        UUID.randomUUID(),
        "code1",
        "proceedingcode1",
                        recordHistory,
        null),
                    createApplicationProceedingEntity(
                        UUID.randomUUID(),
                        "code2",
                        "proceedingcode2",
                        recordHistory,
                        null)
                )
        );

        Application result = applicationMapper.toApplication(applicationEntity);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getClientId()).isEqualTo(clientId);
        assertThat(result.getIsEmergencyApplication()).isTrue();
        assertThat(result.getProviderFirmId()).isEqualTo("firm-001");
        assertThat(result.getProviderOfficeId()).isEqualTo("office-001");
        assertThat(result.getStatementOfCase()).isEqualTo("statementofcase");
        assertThat(result.getCreatedBy()).isEqualTo("admin");
        assertThat(result.getStatusCode()).isEqualTo("NEW");
        assertThat(result.getProceedings().size()).isEqualTo(2);

    }

    @Test
    void shouldMapApplicationCreateRequestToApplicationEntity() {
        UUID clientId = UUID.randomUUID();

        ApplicationCreateRequest applicationRequest = ApplicationCreateRequest.builder()
                .clientId(clientId)
                .isEmergencyApplication(true)
                .providerFirmId("providerFirmId")
                .providerOfficeId("providerOfficeId")
                .statementOfCase("statementOfCase")
                .statusCode("statusCode")
                .proceedings(
                        List.of(
                            ApplicationProceedingCreateRequest
                                .builder()
                                .levelOfServiceCode("servicecode1")
                                .proceedingCode("proceedingcode1")
                                .build(),
                            ApplicationProceedingCreateRequest
                                .builder()
                                .levelOfServiceCode("servicecode2")
                                .proceedingCode("proceedingcode2")
                                .build()
                            )
                        )
                .build();

        ApplicationEntity result = applicationMapper.toApplicationEntity(applicationRequest);

        assertThat(result).isNotNull();
        assertThat(result.getClientId()).isEqualTo(clientId);
        assertThat(result.getIsEmergencyApplication()).isTrue();
        assertThat(result.getProviderFirmId()).isEqualTo("providerFirmId");
        assertThat(result.getProviderOfficeId()).isEqualTo("providerOfficeId");
        assertThat(result.getStatementOfCase()).isEqualTo("statementOfCase");
        assertThat(result.getStatusCode()).isEqualTo("statusCode");
    }
}
