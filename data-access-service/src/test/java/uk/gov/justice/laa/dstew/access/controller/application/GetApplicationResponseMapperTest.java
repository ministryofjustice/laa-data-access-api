package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ApplicationProceedingReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ProviderReadModel;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.getapplication.ApplicationReadModelGenerator;

class GetApplicationResponseMapperTest {

  private GetApplicationResponseMapper responseMapper;

  @BeforeEach
  void setUp() {
    responseMapper = new GetApplicationResponseMapper();
  }

  @Test
  void givenFullyPopulatedReadModel_whenMapped_thenAllResponseFieldsAreCorrect() {
    ApplicationReadModel readModel =
        DataGenerator.createDefault(ApplicationReadModelGenerator.class);

    ResponseEntity<ApplicationResponse> responseEntity =
        responseMapper.toGetApplicationResponse(readModel);

    ApplicationResponse response = responseEntity.getBody();
    assertThat(responseEntity.getStatusCode().value()).isEqualTo(200);
    assertThat(response).isNotNull();
    assertThat(response.getApplicationId()).isEqualTo(readModel.id());
    assertThat(response.getStatus()).isEqualTo(ApplicationStatus.valueOf(readModel.status()));
    assertThat(response.getLaaReference()).isEqualTo(readModel.laaReference());
    assertThat(response.getLastUpdated())
        .isEqualTo(OffsetDateTime.ofInstant(readModel.updatedAt(), ZoneOffset.UTC));
    assertThat(response.getAssignedTo()).isEqualTo(readModel.caseworkerId());
    assertThat(response.getSubmittedAt())
        .isEqualTo(OffsetDateTime.ofInstant(readModel.submittedAt(), ZoneOffset.UTC));
    assertThat(response.getIsLead()).isEqualTo(readModel.isLead());
    assertThat(response.getUsedDelegatedFunctions()).isEqualTo(readModel.usedDelegatedFunctions());
    assertThat(response.getAutoGrant()).isEqualTo(readModel.autoGrant());
    assertThat(response.getDecisionStatus())
        .isEqualTo(DecisionStatus.valueOf(readModel.decisionStatus()));
    assertThat(response.getApplicationType())
        .isEqualTo(ApplicationType.valueOf(readModel.applicationType()));
    assertThat(response.getVersion()).isEqualTo(readModel.version());
    assertThat(response.getOpponents()).hasSize(1);
    assertThat(response.getProvider().getOfficeCode()).isEqualTo(readModel.provider().officeCode());
    assertThat(response.getProvider().getContactEmail())
        .isEqualTo(readModel.provider().contactEmail());
    assertThat(response.getProceedings()).hasSize(1);
    assertThat(response.getProceedings().getFirst().getCategoryOfLaw())
        .isEqualTo(CategoryOfLaw.FAMILY);
    assertThat(response.getProceedings().getFirst().getMatterType())
        .isEqualTo(MatterType.SPECIAL_CHILDREN_ACT);
    assertThat(response.getProceedings().getFirst().getMeritsDecision())
        .isEqualTo(MeritsDecisionStatus.REFUSED);
  }

  @Test
  void givenNullCaseworkerId_whenMapped_thenAssignedToIsNull() {
    ApplicationReadModel readModel =
        DataGenerator.createDefault(
            ApplicationReadModelGenerator.class, builder -> builder.caseworkerId(null));

    ApplicationResponse response = responseMapper.toGetApplicationResponse(readModel).getBody();

    assertThat(response).isNotNull();
    assertThat(response.getAssignedTo()).isNull();
  }

  @Test
  void givenNullSubmittedAt_whenMapped_thenSubmittedAtIsNull() {
    ApplicationReadModel readModel =
        DataGenerator.createDefault(
            ApplicationReadModelGenerator.class, builder -> builder.submittedAt(null));

    ApplicationResponse response = responseMapper.toGetApplicationResponse(readModel).getBody();

    assertThat(response).isNotNull();
    assertThat(response.getSubmittedAt()).isNull();
  }

  @Test
  void givenNullDecisionStatus_whenMapped_thenDecisionStatusIsNull() {
    ApplicationReadModel readModel =
        DataGenerator.createDefault(
            ApplicationReadModelGenerator.class, builder -> builder.decisionStatus(null));

    ApplicationResponse response = responseMapper.toGetApplicationResponse(readModel).getBody();

    assertThat(response).isNotNull();
    assertThat(response.getDecisionStatus()).isNull();
  }

  @Test
  void givenNullProvider_whenMapped_thenProviderIsNull() {
    ApplicationReadModel readModel =
        DataGenerator.createDefault(
            ApplicationReadModelGenerator.class, builder -> builder.provider(null));

    ApplicationResponse response = responseMapper.toGetApplicationResponse(readModel).getBody();

    assertThat(response).isNotNull();
    assertThat(response.getProvider()).isNull();
  }

  @Test
  void givenEmptyProceedings_whenMapped_thenProceedingsIsEmpty() {
    ApplicationReadModel readModel =
        DataGenerator.createDefault(
            ApplicationReadModelGenerator.class, builder -> builder.proceedings(List.of()));

    ApplicationResponse response = responseMapper.toGetApplicationResponse(readModel).getBody();

    assertThat(response).isNotNull();
    assertThat(response.getProceedings()).isEmpty();
  }

  @Test
  void givenEmptyOpponents_whenMapped_thenOpponentsIsEmpty() {
    ApplicationReadModel readModel =
        DataGenerator.createDefault(
            ApplicationReadModelGenerator.class, builder -> builder.opponents(List.of()));

    ApplicationResponse response = responseMapper.toGetApplicationResponse(readModel).getBody();

    assertThat(response).isNotNull();
    assertThat(response.getOpponents()).isEmpty();
  }

  @Test
  void givenNullProceedingMeritsDecision_whenMapped_thenMeritsDecisionIsNull() {
    ApplicationProceedingReadModel proceeding =
        DataGenerator.createDefault(
            uk.gov.justice.laa.dstew.access.utils.generator.getapplication
                .ApplicationProceedingReadModelGenerator.class,
            builder -> builder.meritsDecision(null));
    ApplicationReadModel readModel =
        DataGenerator.createDefault(
            ApplicationReadModelGenerator.class,
            builder -> builder.proceedings(List.of(proceeding)));

    ApplicationResponse response = responseMapper.toGetApplicationResponse(readModel).getBody();

    assertThat(response).isNotNull();
    assertThat(response.getProceedings().getFirst().getMeritsDecision()).isNull();
  }

  @Test
  void givenEmptyStringCategoryOfLaw_whenMapped_thenCategoryOfLawIsNull() {
    ApplicationProceedingReadModel proceeding =
        DataGenerator.createDefault(
            uk.gov.justice.laa.dstew.access.utils.generator.getapplication
                .ApplicationProceedingReadModelGenerator.class,
            builder -> builder.categoryOfLaw(""));
    ApplicationReadModel readModel =
        DataGenerator.createDefault(
            ApplicationReadModelGenerator.class,
            builder -> builder.proceedings(List.of(proceeding)));

    ApplicationResponse response = responseMapper.toGetApplicationResponse(readModel).getBody();

    assertThat(response).isNotNull();
    assertThat(response.getProceedings().getFirst().getCategoryOfLaw()).isNull();
  }

  @Test
  void givenEmptyStringMatterType_whenMapped_thenMatterTypeIsNull() {
    ApplicationProceedingReadModel proceeding =
        DataGenerator.createDefault(
            uk.gov.justice.laa.dstew.access.utils.generator.getapplication
                .ApplicationProceedingReadModelGenerator.class,
            builder -> builder.matterType(""));
    ApplicationReadModel readModel =
        DataGenerator.createDefault(
            ApplicationReadModelGenerator.class,
            builder -> builder.proceedings(List.of(proceeding)));

    ApplicationResponse response = responseMapper.toGetApplicationResponse(readModel).getBody();

    assertThat(response).isNotNull();
    assertThat(response.getProceedings().getFirst().getMatterType()).isNull();
  }

  @Test
  void givenProviderWithNullEmail_whenMapped_thenContactEmailIsNull() {
    ApplicationReadModel readModel =
        DataGenerator.createDefault(
            ApplicationReadModelGenerator.class,
            builder ->
                builder.provider(
                    ProviderReadModel.builder()
                        .officeCode("officeCode")
                        .contactEmail(null)
                        .build()));

    ApplicationResponse response = responseMapper.toGetApplicationResponse(readModel).getBody();

    assertThat(response).isNotNull();
    assertThat(response.getProvider()).isNotNull();
    assertThat(response.getProvider().getContactEmail()).isNull();
  }

  @Test
  void givenNullOpponents_whenMapped_thenOpponentsIsEmpty() {
    ApplicationReadModel readModel =
        DataGenerator.createDefault(
            ApplicationReadModelGenerator.class, builder -> builder.opponents(null));

    ApplicationResponse response = responseMapper.toGetApplicationResponse(readModel).getBody();

    assertThat(response).isNotNull();
    assertThat(response.getOpponents()).isEmpty();
  }

  @Test
  void givenNullProceedings_whenMapped_thenProceedingsIsEmpty() {
    ApplicationReadModel readModel =
        DataGenerator.createDefault(
            ApplicationReadModelGenerator.class, builder -> builder.proceedings(null));

    ApplicationResponse response = responseMapper.toGetApplicationResponse(readModel).getBody();

    assertThat(response).isNotNull();
    assertThat(response.getProceedings()).isEmpty();
  }

  @Test
  void givenNullStatus_whenMapped_thenStatusIsNull() {
    ApplicationReadModel readModel =
        DataGenerator.createDefault(
            ApplicationReadModelGenerator.class, builder -> builder.status(null));

    ApplicationResponse response = responseMapper.toGetApplicationResponse(readModel).getBody();

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isNull();
  }

  @Test
  void givenProceedingWithNullChildrenAndScope_whenMapped_thenNestedListsAreEmpty() {
    ApplicationProceedingReadModel proceeding =
        DataGenerator.createDefault(
            uk.gov.justice.laa.dstew.access.utils.generator.getapplication
                .ApplicationProceedingReadModelGenerator.class,
            builder -> builder.involvedChildren(null).scopeLimitations(null));
    ApplicationReadModel readModel =
        DataGenerator.createDefault(
            ApplicationReadModelGenerator.class,
            builder -> builder.proceedings(List.of(proceeding)));

    ApplicationResponse response = responseMapper.toGetApplicationResponse(readModel).getBody();

    assertThat(response).isNotNull();
    assertThat(response.getProceedings()).hasSize(1);
    assertThat(response.getProceedings().getFirst().getInvolvedChildren()).isEmpty();
    assertThat(response.getProceedings().getFirst().getScopeLimitations()).isEmpty();
  }
}
