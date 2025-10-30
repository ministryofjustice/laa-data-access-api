package uk.gov.justice.laa.dstew.access.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ApplicationMapperTest {

  @InjectMocks
  private ApplicationMapper applicationMapper = new ApplicationMapperImpl();

  private ApplicationEntity createApplicationEntity(
      UUID id,
      UUID clientId,
      String firmId,
      String officeId,
      String statusCode,
      Boolean isEmergency,
      String statement,
      Instant createdAt,
      String createdBy,
      Instant updatedAt,
      String updatedBy
  ) {
    ApplicationEntity applicationEntity = new ApplicationEntity();
    applicationEntity.setId(id);
    applicationEntity.setClientId(clientId);
    applicationEntity.setProviderFirmId(firmId);
    applicationEntity.setProviderOfficeId(officeId);
    applicationEntity.setStatusCode(statusCode);
    applicationEntity.setIsEmergencyApplication(isEmergency);
    applicationEntity.setStatementOfCase(statement);
    applicationEntity.setCreatedAt(createdAt);
    applicationEntity.setCreatedBy(createdBy);
    applicationEntity.setUpdatedAt(updatedAt);
    applicationEntity.setUpdatedBy(updatedBy);
    return applicationEntity;
  }

  @Test
  void shouldMapApplicationEntityToApplication() {
    UUID id = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();

    Instant now = Instant.now();

    ApplicationEntity applicationEntity = createApplicationEntity(
        id,
        clientId,
        "firm-001",
        "office-001",
        "NEW",
        true,
        "statementofcase",
        now,
        "admin",
        now,
        "admin"
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
    assertThat(result.getCreatedAt()).isEqualTo(now.atOffset(ZoneOffset.UTC));
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
}
