package uk.gov.justice.laa.dstew.access.service.application;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceeding;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetails;
import uk.gov.justice.laa.dstew.access.model.RefusalDetails;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class MakeDecisionForApplicationTest extends BaseServiceTest {

    @Autowired
    private ApplicationService serviceUnderTest;

    @Test
    void givenMakeDecisionRequestWithTwoProceedings_whenAssignDecision_thenDecisionSaved() {
        UUID applicationId = UUID.randomUUID();
        UUID grantedProceedingId = UUID.randomUUID();
        UUID refusedProceedingId = UUID.randomUUID();

        MeritsDecisionStatus grantedDecision = MeritsDecisionStatus.GRANTED;
        String grantedReason = "refusal 1";
        String grantedJustification = "justification 1";

        MeritsDecisionStatus refusedDecision = MeritsDecisionStatus.REFUSED;
        String refusedReason = "refusal 2";
        String refusedJustification = "justification 2";

        // given
        CaseworkerEntity caseworker = caseworkerFactory.createDefault();

        // overwrite some fields of default assign decision request
        MakeDecisionRequest makeDecisionRequest = applicationMakeDecisionRequestFactory.createDefault(requestBuilder ->
                requestBuilder
                        .userId(caseworker.getId())
                        .proceedings(List.of(
                                createMakeDecisionProceedingDetails(grantedProceedingId, grantedDecision, grantedReason, grantedJustification),
                                createMakeDecisionProceedingDetails(refusedProceedingId, refusedDecision, refusedReason, refusedJustification)
                        ))
        );

        // expected saved application entity
        ApplicationEntity expectedApplicationEntity = applicationEntityFactory
                .createDefault(builder ->
                        builder
                                .id(applicationId)
                                .applicationContent(new HashMap<>(Map.of("test", "unmodified")))
                                .caseworker(caseworker)
                );

        setSecurityContext(TestConstants.Roles.WRITER);

        ProceedingEntity grantedProceedingEntity = proceedingsEntityFactory
                .createDefault(builder ->
                        builder.id(grantedProceedingId)
                );

        ProceedingEntity refusedProceedingEntity = proceedingsEntityFactory
                .createDefault(builder ->
                        builder.id(refusedProceedingId)
                );

        // when
        when(caseworkerRepository.findById(caseworker.getId()))
                .thenReturn(Optional.of(caseworker));
        when(proceedingRepository.findById(grantedProceedingId))
                .thenReturn(Optional.of(grantedProceedingEntity));
        when(proceedingRepository.findById(refusedProceedingId))
                .thenReturn(Optional.of(refusedProceedingEntity));
        when(applicationRepository.findById(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));
        when(decisionRepository.findByApplicationId(expectedApplicationEntity.getId()))
                .thenReturn(Optional.empty());

        serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest);

        // then
        verify(applicationRepository, times(1)).findById(expectedApplicationEntity.getId());
        verify(decisionRepository, times(1)).findByApplicationId(expectedApplicationEntity.getId());
        verify(applicationRepository, times(1)).save(any(ApplicationEntity.class));
        verifyDecisionSavedCorrectly(makeDecisionRequest, expectedApplicationEntity, null, 2);
    }

    @Test
    void givenApplicationAndExistingDecision_whenAssignDecision_thenDecisionUpdated() {
        UUID applicationId = UUID.randomUUID();
        UUID proceedingId = UUID.randomUUID();

        // given
        CaseworkerEntity caseworker = caseworkerFactory.createDefault();

        MakeDecisionRequest makeDecisionRequest = applicationMakeDecisionRequestFactory.createDefault(requestBuilder ->
                requestBuilder
                        .userId(caseworker.getId())
                        .proceedings(List.of(
                                createMakeDecisionProceedingDetails(proceedingId, MeritsDecisionStatus.GRANTED, "refusal update", "justification update")
                        ))
        );

        // expected saved application entity
        ApplicationEntity expectedApplicationEntity = applicationEntityFactory
                .createDefault(builder ->
                        builder
                                .id(applicationId)
                                .applicationContent(new HashMap<>(Map.of("test", "unmodified")))
                                .caseworker(caseworker)
                );

        DecisionEntity currentSavedDecisionEntity = createDecisionEntity(
                applicationId,
                proceedingId,
                MeritsDecisionStatus.REFUSED,
                "initial reason",
                "initial justification"
        );

        setSecurityContext(TestConstants.Roles.WRITER);

        ProceedingEntity proceedingEntity = proceedingsEntityFactory
                .createDefault(builder ->
                        builder.id(proceedingId)
                );

        // when
        when(proceedingRepository.findById(proceedingId))
                .thenReturn(Optional.of(proceedingEntity));
        when(caseworkerRepository.findById(caseworker.getId()))
                .thenReturn(Optional.of(caseworker));
        when(applicationRepository.findById(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));
        when(decisionRepository.findByApplicationId(expectedApplicationEntity.getId()))
                .thenReturn(Optional.of(currentSavedDecisionEntity));

        serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest);

        // then
        verify(applicationRepository, times(1)).findById(expectedApplicationEntity.getId());
        verify(decisionRepository, times(1)).findByApplicationId(expectedApplicationEntity.getId());
        verify(applicationRepository, times(1)).save(any(ApplicationEntity.class));

        verifyDecisionSavedCorrectly(makeDecisionRequest, expectedApplicationEntity, currentSavedDecisionEntity, 1);
    }

    @Test
    void givenApplicationAndExistingDecisionAndNewProceeding_whenAssignDecision_thenDecisionUpdated() {
        UUID applicationId = UUID.randomUUID();
        UUID proceedingId = UUID.randomUUID();
        UUID newProceedingId = UUID.randomUUID();

        // given
        CaseworkerEntity caseworker = caseworkerFactory.createDefault();

        MakeDecisionRequest makeDecisionRequest = applicationMakeDecisionRequestFactory.createDefault(requestBuilder ->
                requestBuilder
                        .userId(caseworker.getId())
                        .proceedings(List.of(
                                createMakeDecisionProceedingDetails(newProceedingId, MeritsDecisionStatus.GRANTED, "new refusal", "new justification")
                        ))
        );

        // expected saved application entity
        ApplicationEntity expectedApplicationEntity = applicationEntityFactory
                .createDefault(builder ->
                        builder
                                .id(applicationId)
                                .applicationContent(new HashMap<>(Map.of("test", "unmodified")))
                                .caseworker(caseworker)
                );

        DecisionEntity currentSavedDecisionEntity = createDecisionEntity(
                applicationId,
                proceedingId,
                MeritsDecisionStatus.REFUSED,
                "initial reason",
                "initial justification"
        );

        setSecurityContext(TestConstants.Roles.WRITER);

        ProceedingEntity existingProceedingEntity = proceedingsEntityFactory
                .createDefault(builder ->
                        builder.id(proceedingId)
                );

        ProceedingEntity newProceedingEntity = proceedingsEntityFactory
                .createDefault(builder ->
                        builder.id(newProceedingId)
                );

        // when
        when(proceedingRepository.findById(proceedingId))
                .thenReturn(Optional.of(existingProceedingEntity));
        when(proceedingRepository.findById(newProceedingId))
                .thenReturn(Optional.of(newProceedingEntity));
        when(caseworkerRepository.findById(caseworker.getId()))
                .thenReturn(Optional.of(caseworker));
        when(applicationRepository.findById(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));
        when(decisionRepository.findByApplicationId(expectedApplicationEntity.getId()))
                .thenReturn(Optional.of(currentSavedDecisionEntity));

        serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest);

        // then
        verify(applicationRepository, times(1)).findById(expectedApplicationEntity.getId());
        verify(decisionRepository, times(1)).findByApplicationId(expectedApplicationEntity.getId());
        verify(applicationRepository, times(1)).save(any(ApplicationEntity.class));

        verifyDecisionSavedCorrectly(makeDecisionRequest, expectedApplicationEntity, currentSavedDecisionEntity, 2);
    }

    @Test
    void givenApplicationAndDecisionRequestWithMultipleProceedingsAndExistingDecision_whenAssignDecision_thenDecisionUpdated() {
        UUID applicationId = UUID.randomUUID();
        UUID currentProceedingId = UUID.randomUUID();
        UUID newProceedingId = UUID.randomUUID();

        // given
        CaseworkerEntity caseworker = caseworkerFactory.createDefault();

        MakeDecisionRequest makeDecisionRequest = applicationMakeDecisionRequestFactory.createDefault(requestBuilder ->
                requestBuilder
                        .userId(caseworker.getId())
                        .proceedings(List.of(
                                createMakeDecisionProceedingDetails(newProceedingId, MeritsDecisionStatus.REFUSED, "refusal new", "justification new"),
                                createMakeDecisionProceedingDetails(currentProceedingId, MeritsDecisionStatus.GRANTED, "refusal update", "justification update")
                        ))
        );

        // expected saved application entity
        ApplicationEntity expectedApplicationEntity = applicationEntityFactory
                .createDefault(builder ->
                        builder
                                .id(applicationId)
                                .applicationContent(new HashMap<>(Map.of("test", "unmodified")))
                                .caseworker(caseworker)
                );

        DecisionEntity currentSavedDecisionEntity = createDecisionEntity(
                applicationId,
                currentProceedingId,
                MeritsDecisionStatus.REFUSED,
                "current reason",
                "current justification"
        );

        ProceedingEntity currentProceedingEntity = proceedingsEntityFactory
                .createDefault(builder ->
                        builder.id(currentProceedingId)
                );

        ProceedingEntity newProceedingEntity = proceedingsEntityFactory
                .createDefault(builder ->
                        builder.id(newProceedingId)
                );

        setSecurityContext(TestConstants.Roles.WRITER);

        // when
        when(proceedingRepository.findById(currentProceedingId))
                .thenReturn(Optional.of(currentProceedingEntity));
        when(proceedingRepository.findById(newProceedingId))
                .thenReturn(Optional.of(newProceedingEntity));
        when(caseworkerRepository.findById(caseworker.getId()))
                .thenReturn(Optional.of(caseworker));
        when(applicationRepository.findById(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));
        when(decisionRepository.findByApplicationId(expectedApplicationEntity.getId()))
                .thenReturn(Optional.of(currentSavedDecisionEntity));

        serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest);

        // then
        verify(applicationRepository, times(1)).findById(expectedApplicationEntity.getId());
        verify(decisionRepository, times(1)).findByApplicationId(expectedApplicationEntity.getId());
        verify(applicationRepository, times(1)).save(any(ApplicationEntity.class));

        verifyDecisionSavedCorrectly(makeDecisionRequest, expectedApplicationEntity, currentSavedDecisionEntity, 2);
    }

    @Test
    void givenNoCaseworker_whenAssignDecision_thenThrowResourceNotFoundException() {
        UUID applicationId = UUID.randomUUID();
        UUID caseworkerId = UUID.randomUUID();

        // given
        MakeDecisionRequest makeDecisionRequest = applicationMakeDecisionRequestFactory
                .createDefault(requestBuilder ->
                        requestBuilder
                                .userId(caseworkerId)
                );

        // expected saved application entity
        ApplicationEntity expectedApplicationEntity = applicationEntityFactory
                .createDefault(builder ->
                        builder
                                .id(applicationId)
                                .applicationContent(new HashMap<>(Map.of("test", "unmodified")))
                );

        setSecurityContext(TestConstants.Roles.WRITER);

        when(applicationRepository.findById(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));

        Throwable thrown = catchThrowable(() ->
                serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest));

        assertThat(thrown)
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No caseworker found with id: "+caseworkerId.toString());

    }

    @Test
    void givenNoApplication_whenAssignDecision_thenThrowResourceNotFoundException() {
        UUID applicationId = UUID.randomUUID();

        // given
        MakeDecisionRequest makeDecisionRequest = applicationMakeDecisionRequestFactory.createDefault();

        // expected saved application entity
        ApplicationEntity expectedApplicationEntity = applicationEntityFactory
                .createDefault(builder ->
                        builder
                                .id(applicationId)
                                .applicationContent(new HashMap<>(Map.of("test", "unmodified")))
                );

        setSecurityContext(TestConstants.Roles.WRITER);

        Throwable thrown = catchThrowable(() ->
                serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest));

        assertThat(thrown)
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No application found with id: "+applicationId.toString());
    }

    @Test
    void givenNoProceeding_whenAssignDecision_thenThrowResourceNotFoundException() {
        UUID applicationId = UUID.randomUUID();
        UUID proceedingId = UUID.randomUUID();

        MeritsDecisionStatus decision = MeritsDecisionStatus.GRANTED;
        String reason = "refusal 1";
        String justification = "justification 1";

        // given
        CaseworkerEntity caseworker = caseworkerFactory.createDefault();

        // overwrite some fields of default assign decision request
        MakeDecisionRequest makeDecisionRequest = applicationMakeDecisionRequestFactory.createDefault(requestBuilder ->
                requestBuilder
                        .userId(caseworker.getId())
                        .proceedings(List.of(
                                createMakeDecisionProceedingDetails(proceedingId, decision, reason, justification)
                        ))
        );

        // expected saved application entity
        ApplicationEntity expectedApplicationEntity = applicationEntityFactory
                .createDefault(builder ->
                        builder
                                .id(applicationId)
                                .applicationContent(new HashMap<>(Map.of("test", "unmodified")))
                                .caseworker(caseworker)
                );

        setSecurityContext(TestConstants.Roles.WRITER);

        ProceedingEntity proceedingEntity = proceedingsEntityFactory
                .createDefault(builder ->
                        builder.id(proceedingId)
                );

        // when
        when(applicationRepository.findById(expectedApplicationEntity.getId())).thenReturn(Optional.of(expectedApplicationEntity));
        when(caseworkerRepository.findById(caseworker.getId()))
                .thenReturn(Optional.of(caseworker));
        when(decisionRepository.findByApplicationId(expectedApplicationEntity.getId()))
                .thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(
                () -> serviceUnderTest.makeDecision(expectedApplicationEntity.getId(), makeDecisionRequest)
        );

        // then
        assertThat(thrown)
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No proceeding found with id: " + proceedingId);
    }

    private DecisionEntity createDecisionEntity(UUID applicationId, UUID proceedingId, MeritsDecisionStatus decision, String reason, String justification) {
        return decisionEntityFactory.createDefault(
                builder -> builder
                        .id(UUID.randomUUID())
                        .applicationId(applicationId)
                        .createdAt(Instant.now())
                        .overallDecision(DecisionStatus.PARTIALLY_GRANTED)
                        .meritsDecisions(
                                Set.of(
                                        meritsDecisionsEntityFactory.createDefault(
                                                meritsDecisionBuilder ->
                                                        meritsDecisionBuilder
                                                                .id(UUID.randomUUID())
                                                                .createdAt(Instant.now())
                                                                .modifiedAt(Instant.now())
                                                                .proceeding(
                                                                        proceedingsEntityFactory.createDefault(
                                                                                proceedingsBuilder ->
                                                                                        proceedingsBuilder.id(proceedingId)
                                                                        )
                                                                )
                                                                .decision(decision)
                                                                .reason(reason)
                                                                .justification(justification)
                                        )
                                )
                        )
        );
    }

    private MakeDecisionProceeding createMakeDecisionProceedingDetails(UUID proceedingId, MeritsDecisionStatus decision, String reason, String justification) {
        return makeDecisionProceedingFactory.createDefault(proceedingsBuilder ->
                proceedingsBuilder
                        .proceedingId(proceedingId)
                        .meritsDecision(
                                meritsDecisionDetailsFactory.createDefault(meritsDecisionBuilder ->
                                        meritsDecisionBuilder
                                                .decision(decision)
                                                .refusal(
                                                        refusalDetailsFactory.createDefault(refusalBuilder ->
                                                                refusalBuilder
                                                                        .reason(reason)
                                                                        .justification(justification)
                                                        )
                                                )
                                )
                        )
        );
    }

    private void verifyDecisionSavedCorrectly(
            MakeDecisionRequest expectedMakeDecisionRequest,
            ApplicationEntity expectedApplicationEntity,
            DecisionEntity currentSavedDecisionEntity,
            int expectedNumberOfMeritsDecisions) {

        ArgumentCaptor<DecisionEntity> decisionCaptor = ArgumentCaptor.forClass(DecisionEntity.class);
        verify(decisionRepository, times(1)).save(decisionCaptor.capture());
        DecisionEntity savedDecision = decisionCaptor.getValue();

        MakeDecisionRequest actual = mapToMakeDecisionRequest(savedDecision, expectedApplicationEntity);

        assertThat(actual.getProceedings().size()).isEqualTo(expectedNumberOfMeritsDecisions);

        // only check general fields, not proceedings as there may be a size discrepancy if
        // adding a new merits decision.
        assertThat(actual)
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .ignoringFields("proceedings")
                .isEqualTo(expectedMakeDecisionRequest);

        // Assert only on the set of proceedings that are updated or added via the request.
        Set<UUID> expectedProceedingIds = expectedMakeDecisionRequest.getProceedings().stream()
                .map(MakeDecisionProceeding::getProceedingId)
                .collect(Collectors.toSet());
        List<MakeDecisionProceeding> actualProceedings = actual.getProceedings().stream()
                .filter(p -> expectedProceedingIds.contains(p.getProceedingId()))
                .collect(Collectors.toList());
        assertThat(actualProceedings)
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .isEqualTo(expectedMakeDecisionRequest.getProceedings());

        assertThat(savedDecision.getModifiedAt()).isNotNull();
        assertThat(savedDecision.getMeritsDecisions())
                .allSatisfy(merits -> {
                    assertThat(merits.getModifiedAt()).isNotNull();
                });
    }

    // DecisionEntity -> MakeDecisionRequest
    private static MakeDecisionRequest mapToMakeDecisionRequest(DecisionEntity decisionEntity, ApplicationEntity applicationEntity) {
        if (decisionEntity == null) return null;
        return MakeDecisionRequest.builder()
                .applicationStatus(applicationEntity.getStatus())
                .overallDecision(decisionEntity.getOverallDecision())
                .userId(applicationEntity.getCaseworker().getId())
                .proceedings(decisionEntity.getMeritsDecisions().stream()
                        .map(MakeDecisionForApplicationTest::mapToProceedingDetails)
                        .toList())
                .build();
    }

    // MeritsDecisionEntity -> ProceedingDetails
    private static MakeDecisionProceeding mapToProceedingDetails(MeritsDecisionEntity meritsDecisionEntity) {
        if (meritsDecisionEntity == null) return null;
        return MakeDecisionProceeding.builder()
                .proceedingId(meritsDecisionEntity.getProceeding().getId())
                .meritsDecision(mapToMeritsDecisionDetails(meritsDecisionEntity))
                .build();
    }

    // MeritsDecisionEntity -> MeritsDecisionDetails
    private static MeritsDecisionDetails mapToMeritsDecisionDetails(MeritsDecisionEntity entity) {
        if (entity == null) return null;
        return MeritsDecisionDetails.builder()
                .decision(entity.getDecision())
                .refusal(mapToRefusalDetails(entity))
                .build();
    }

    // MeritsDecisionEntity -> RefusalDetails
    private static RefusalDetails mapToRefusalDetails(MeritsDecisionEntity entity) {
        if (entity == null) return null;
        return RefusalDetails.builder()
                .reason(entity.getReason())
                .justification(entity.getJustification())
                .build();
    }
}
