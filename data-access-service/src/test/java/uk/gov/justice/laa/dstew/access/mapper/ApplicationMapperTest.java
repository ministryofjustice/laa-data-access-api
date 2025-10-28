package uk.gov.justice.laa.dstew.access.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.*;

import uk.gov.justice.laa.dstew.access.model.*;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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

    private ApplicationProceedingUpdateRequest createApplicationProceedingUpdateRequest(
            UUID id,
            String proceedingCode,
            String levelOfServiceCode
    ) {
        return ApplicationProceedingUpdateRequest.builder()
                .id(id)
                .proceedingCode(proceedingCode)
                .levelOfServiceCode(levelOfServiceCode)
                .build();
    }

    private ApplicationProceedingCreateRequest createApplicationProceedingCreateRequest(
            String proceedingCode,
            String levelOfServiceCode
    ) {
        return ApplicationProceedingCreateRequest.builder()
                .proceedingCode(proceedingCode)
                .levelOfServiceCode(levelOfServiceCode)
                .build();
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
                                createApplicationProceedingCreateRequest(
                                        "servicecode1",
                                        "proceedingcode1"
                                ),
                                createApplicationProceedingCreateRequest(
                                        "servicecode2",
                                        "proceedingcode2"
                                )
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
        assertThat(result.getProceedings()).isEmpty();
    }

    @Test
    void shouldMapApplicationUpdateRequestToApplicationEntity() {
        UUID clientId = UUID.randomUUID();

        ApplicationUpdateRequest applicationRequest = ApplicationUpdateRequest.builder()
                .clientId(clientId)
                .isEmergencyApplication(true)
                .providerFirmId("providerFirmId")
                .providerOfficeId("providerOfficeId")
                .statementOfCase("statementOfCase")
                .statusCode("statusCode")
                .proceedings(
                        List.of(createApplicationProceedingUpdateRequest(
                                        UUID.randomUUID(),
                                        "servicecode1",
                                        "proceedingcode1"
                                ),
                                createApplicationProceedingUpdateRequest(
                                        UUID.randomUUID(),
                                        "servicecode2",
                                        "proceedingcode2"
                                )
                        )
                )
                .build();

        ApplicationEntity result = new ApplicationEntity();
        applicationMapper.updateApplicationEntity(result, applicationRequest);

        assertThat(result).isNotNull();
        assertThat(result.getClientId()).isEqualTo(clientId);
        assertThat(result.getIsEmergencyApplication()).isTrue();
        assertThat(result.getProviderFirmId()).isEqualTo("providerFirmId");
        assertThat(result.getProviderOfficeId()).isEqualTo("providerOfficeId");
        assertThat(result.getStatementOfCase()).isEqualTo("statementOfCase");
        assertThat(result.getStatusCode()).isEqualTo("statusCode");
        assertThat(result.getUpdatedAt()).isNull();
        assertThat(result.getProceedings()).isEmpty();
    }

    @Test
    void shouldMapApplicationUpdateRequestToApplication() {
        UUID clientId = UUID.randomUUID();

        ApplicationUpdateRequest applicationRequest = ApplicationUpdateRequest.builder()
                .clientId(clientId)
                .isEmergencyApplication(true)
                .providerFirmId("firm-001")
                .providerOfficeId("office-001")
                .statementOfCase("statementOfCase")
                .statusCode("NEW")
                .proceedings(
                        List.of(
                                ApplicationProceedingUpdateRequest
                                        .builder()
                                        .id(UUID.randomUUID())
                                        .levelOfServiceCode("servicecode1")
                                        .proceedingCode("proceedingcode1")
                                        .build(),
                                ApplicationProceedingUpdateRequest
                                        .builder()
                                        .id(UUID.randomUUID())
                                        .levelOfServiceCode("servicecode2")
                                        .proceedingCode("proceedingcode2")
                                        .build()
                        )
                )
                .build();

        Application result = new Application();
        applicationMapper.updateApplicationEntity(result, applicationRequest);

        assertThat(result).isNotNull();
        assertThat(result.getClientId()).isEqualTo(clientId);
        assertThat(result.getIsEmergencyApplication()).isTrue();
        assertThat(result.getProviderFirmId()).isEqualTo("firm-001");
        assertThat(result.getProviderOfficeId()).isEqualTo("office-001");
        assertThat(result.getStatementOfCase()).isEqualTo("statementOfCase");
        assertThat(result.getCreatedBy()).isNull();
        assertThat(result.getStatusCode()).isEqualTo("NEW");
        assertThat(result.getProceedings().size()).isEqualTo(2);
    }

    @Test
    void shouldMapApplicationProceedingUpdateRequestToApplicationProceeding() {
        ApplicationProceedingUpdateRequest applicationRequest =
                createApplicationProceedingUpdateRequest(
                        UUID.randomUUID(),
                        "proceedingCode",
                        "levelCode"
                );

        ApplicationProceeding result = applicationMapper.toApplicationProceeding(applicationRequest);

        assertThat(result).isNotNull();
        assertThat(result.getProceedingCode()).isEqualTo("proceedingCode");
        assertThat(result.getLevelOfServiceCode()).isEqualTo("levelCode");
        assertThat(result.getCreatedBy()).isNull();
    }

    @Test
    void toOffsetDateTime_shouldReturnNull_whenInstantIsNull() {
        OffsetDateTime result = applicationMapper.toOffsetDateTime(null);
        assertThat(result).isNull();
    }

    @Test
    void toOffsetDateTime_shouldConvertInstantToOffsetDateTime_whenInstantIsNotNull() {
        Instant now = Instant.parse("2023-01-01T12:00:00Z");
        OffsetDateTime result = applicationMapper.toOffsetDateTime(now);

        assertThat(result).isEqualTo(now.atOffset(ZoneOffset.UTC));
    }

    @Test
    void shouldNotOverwriteFieldsWhenUpdateRequestHasNulls_applicationEntity() {
        ApplicationEntity existing = new ApplicationEntity();
        UUID originalClientId = UUID.randomUUID();
        existing.setClientId(originalClientId);
        existing.setProviderFirmId("existingFirmId");
        existing.setProviderOfficeId("existingOfficeId");
        existing.setStatementOfCase("existingStatement");
        existing.setStatusCode("EXISTING");

        ApplicationUpdateRequest updateRequest = ApplicationUpdateRequest.builder()
                .clientId(null)
                .providerFirmId(null)
                .providerOfficeId(null)
                .statementOfCase(null)
                .statusCode(null)
                .build();

        applicationMapper.updateApplicationEntity(existing, updateRequest);

        assertThat(existing.getClientId()).isEqualTo(originalClientId);
        assertThat(existing.getProviderFirmId()).isEqualTo("existingFirmId");
        assertThat(existing.getProviderOfficeId()).isEqualTo("existingOfficeId");
        assertThat(existing.getStatementOfCase()).isEqualTo("existingStatement");
        assertThat(existing.getStatusCode()).isEqualTo("EXISTING");
    }

    @Test
    void shouldNotOverwriteFieldsWhenUpdateRequestHasNulls_applicationDto() {
        Application existing = new Application();
        UUID originalClientId = UUID.randomUUID();
        existing.setClientId(originalClientId);
        existing.setProviderFirmId("existingFirmId");
        existing.setProviderOfficeId("existingOfficeId");
        existing.setStatementOfCase("existingStatement");
        existing.setStatusCode("EXISTING");

        ApplicationUpdateRequest updateRequest = ApplicationUpdateRequest.builder()
                .clientId(null)
                .providerFirmId(null)
                .providerOfficeId(null)
                .statementOfCase(null)
                .statusCode(null)
                .build();

        applicationMapper.updateApplicationEntity(existing, updateRequest);

        assertThat(existing.getClientId()).isEqualTo(originalClientId);
        assertThat(existing.getProviderFirmId()).isEqualTo("existingFirmId");
        assertThat(existing.getProviderOfficeId()).isEqualTo("existingOfficeId");
        assertThat(existing.getStatementOfCase()).isEqualTo("existingStatement");
        assertThat(existing.getStatusCode()).isEqualTo("EXISTING");
    }

    @Test
    void shouldHandleEmptyProceedingsListOnCreateRequest() {
        ApplicationCreateRequest createRequest = ApplicationCreateRequest.builder()
                .clientId(UUID.randomUUID())
                .isEmergencyApplication(false)
                .providerFirmId("firm")
                .providerOfficeId("office")
                .statusCode("NEW")
                .statementOfCase("some statement")
                .proceedings(List.of())
                .build();

        ApplicationEntity result = applicationMapper.toApplicationEntity(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getProceedings()).isEmpty();
    }

    @Test
    void shouldHandleNullProceedingsListOnUpdateRequest() {
        ApplicationEntity existing = new ApplicationEntity();
        existing.setProceedings(List.of());

        ApplicationUpdateRequest updateRequest = ApplicationUpdateRequest.builder()
                .proceedings(null)
                .build();

        applicationMapper.updateApplicationEntity(existing, updateRequest);
        assertThat(existing.getProceedings()).isEmpty();
    }

    @Test
    void shouldMapApplicationSummaryEntityToApplicationSummary() {

        StatusCodeLookupEntity statusCodeLookupEntity = new StatusCodeLookupEntity();
        statusCodeLookupEntity.setCode("code");
        statusCodeLookupEntity.setDescription("description");
        statusCodeLookupEntity.setId(UUID.randomUUID());
        statusCodeLookupEntity.setCreatedAt(Instant.now());
        ApplicationSummaryEntity applicationSummaryEntity = new ApplicationSummaryEntity();
        applicationSummaryEntity.setCreatedAt(Instant.now());
        applicationSummaryEntity.setModifiedAt(Instant.now());
        applicationSummaryEntity.setId(UUID.randomUUID());
        applicationSummaryEntity.setStatusCodeLookupEntity(statusCodeLookupEntity);
        ApplicationSummary result = applicationMapper.toApplicationSummary(applicationSummaryEntity);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(applicationSummaryEntity.getId());
        assertThat(result.getApplicationStatus()).isEqualTo(applicationSummaryEntity.getStatusCodeLookupEntity().getDescription());
        assertThat(result.getLastUpdatedAt()).isEqualTo(applicationSummaryEntity.getModifiedAt().toString());
        assertThat(result.getSubmittedAt()).isEqualTo(applicationSummaryEntity.getCreatedAt().toString());
    }

}