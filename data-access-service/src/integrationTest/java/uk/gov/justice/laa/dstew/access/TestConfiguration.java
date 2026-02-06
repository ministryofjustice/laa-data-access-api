package uk.gov.justice.laa.dstew.access;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.repository.DecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.repository.LinkedApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.MeritsDecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;
import uk.gov.justice.laa.dstew.access.utils.factory.PersistedFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationMakeDecisionFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationCreateFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationUpdateFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.application.LinkedApplicationEntityFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerAssignFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerUnassignFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.decision.DecisionFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.decision.MeritsDecisionFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.domainevents.DomainEventFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.individual.IndividualEntityFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.individual.IndividualFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.proceeding.ProceedingFactoryImpl;

@Configuration
public class TestConfiguration {

    @Bean
    public PersistedFactory<
            ApplicationRepository,
            Factory<ApplicationEntity, ApplicationEntity.ApplicationEntityBuilder>,
            ApplicationEntity,
            ApplicationEntity.ApplicationEntityBuilder,
            UUID> persistedApplicationFactory(ApplicationRepository repository, Factory<ApplicationEntity, ApplicationEntity.ApplicationEntityBuilder> applicationFactory) {
        return new PersistedFactory<>(repository, applicationFactory);
    }

    @Bean
    public PersistedFactory<
            CaseworkerRepository,
            Factory<CaseworkerEntity, CaseworkerEntity.CaseworkerEntityBuilder>,
            CaseworkerEntity,
            CaseworkerEntity.CaseworkerEntityBuilder,
            UUID> persistedCaseworkerFactory(CaseworkerRepository repository, Factory<CaseworkerEntity, CaseworkerEntity.CaseworkerEntityBuilder> caseworkerFactory) {
        return new PersistedFactory<>(repository, caseworkerFactory);
    }

    @Bean
    public PersistedFactory<
            DomainEventRepository,
            Factory<DomainEventEntity, DomainEventEntity.DomainEventEntityBuilder>,
            DomainEventEntity,
            DomainEventEntity.DomainEventEntityBuilder,
            UUID> persistedDomainEventFactory(DomainEventRepository repository, Factory<DomainEventEntity, DomainEventEntity.DomainEventEntityBuilder> domainEventFactory) {
        return new PersistedFactory<>(repository, domainEventFactory);
    }

    @Bean
    public PersistedFactory<
            ProceedingRepository,
            Factory<ProceedingEntity, ProceedingEntity.ProceedingEntityBuilder>,
            ProceedingEntity,
            ProceedingEntity.ProceedingEntityBuilder,
            UUID> persistedProceedingFactory(ProceedingRepository repository, Factory<ProceedingEntity, ProceedingEntity.ProceedingEntityBuilder> proceedingFactory) {
        return new PersistedFactory<>(repository, proceedingFactory);
    }

    @Bean
    public PersistedFactory<
            MeritsDecisionRepository,
            Factory<MeritsDecisionEntity, MeritsDecisionEntity.MeritsDecisionEntityBuilder>,
            MeritsDecisionEntity,
            MeritsDecisionEntity.MeritsDecisionEntityBuilder,
            UUID> persistedMeritsDecisionFactory(MeritsDecisionRepository repository, Factory<MeritsDecisionEntity, MeritsDecisionEntity.MeritsDecisionEntityBuilder> meritsDecisionFactory) {
        return new PersistedFactory<>(repository, meritsDecisionFactory);
    }

    @Bean
    public PersistedFactory<
            DecisionRepository,
            Factory<DecisionEntity, DecisionEntity.DecisionEntityBuilder>,
            DecisionEntity,
            DecisionEntity.DecisionEntityBuilder,
            UUID> persistedDecisionFactory(DecisionRepository repository, Factory<DecisionEntity, DecisionEntity.DecisionEntityBuilder> decisionFactory) {
        return new PersistedFactory<>(repository, decisionFactory);
    }

    @Bean
    public PersistedFactory<
        LinkedApplicationRepository,
        Factory<LinkedApplicationEntity, LinkedApplicationEntity.LinkedApplicationEntityBuilder>,
        LinkedApplicationEntity,
        LinkedApplicationEntity.LinkedApplicationEntityBuilder,
        UUID>
    persistedLinkedApplicationFactory(
        LinkedApplicationRepository repository,
        Factory<LinkedApplicationEntity, LinkedApplicationEntity.LinkedApplicationEntityBuilder> factory
    ) {
        return new PersistedFactory<>(repository, factory);
    }

    @Bean
    public PersistedFactory<
            uk.gov.justice.laa.dstew.access.repository.IndividualRepository,
            Factory<uk.gov.justice.laa.dstew.access.entity.IndividualEntity, uk.gov.justice.laa.dstew.access.entity.IndividualEntity.IndividualEntityBuilder>,
            uk.gov.justice.laa.dstew.access.entity.IndividualEntity,
            uk.gov.justice.laa.dstew.access.entity.IndividualEntity.IndividualEntityBuilder,
            UUID> persistedIndividualFactory(
            uk.gov.justice.laa.dstew.access.repository.IndividualRepository repository,
            Factory<uk.gov.justice.laa.dstew.access.entity.IndividualEntity, uk.gov.justice.laa.dstew.access.entity.IndividualEntity.IndividualEntityBuilder> individualEntityFactory) {
        return new PersistedFactory<>(repository, individualEntityFactory);
    }

    @Bean
    public Factory<ApplicationCreateRequest, ApplicationCreateRequest.Builder> applicationCreateRequestFactory() {
        return new ApplicationCreateFactoryImpl();
    }

    @Bean
    public Factory<ApplicationUpdateRequest, ApplicationUpdateRequest.Builder> applicationUpdateRequestFactory() {
        return new ApplicationUpdateFactoryImpl();
    }

    @Bean
    public Factory<CaseworkerAssignRequest, CaseworkerAssignRequest.Builder> caseworkerAssignRequestFactory() {
        return new CaseworkerAssignFactoryImpl();
    }

    @Bean
    public Factory<CaseworkerUnassignRequest, CaseworkerUnassignRequest.Builder> caseworkerUnassignRequestFactory() {
        return new CaseworkerUnassignFactoryImpl();
    }

    @Bean
    public Factory<IndividualEntity, IndividualEntity.IndividualEntityBuilder> individualEntityFactory() {
        return new IndividualEntityFactoryImpl();
    }

    @Bean
    public Factory<CaseworkerEntity, CaseworkerEntity.CaseworkerEntityBuilder> caseworkerFactory() {
        return new CaseworkerFactoryImpl();
    }

    @Bean
    public Factory<DomainEventEntity, DomainEventEntity.DomainEventEntityBuilder> domainEventFactory() {
        return new DomainEventFactoryImpl();
    }

    @Bean
    public Factory<Individual, Individual.Builder> individualFactory() {
        return new IndividualFactoryImpl();
    }

    @Bean
    public Factory<MakeDecisionRequest, MakeDecisionRequest.Builder> applicationMakeDecisionRequestFactory() {
        return new ApplicationMakeDecisionFactoryImpl();
    }

    @Bean
    public Factory<ProceedingEntity, ProceedingEntity.ProceedingEntityBuilder> proceedingFactory() {
        return new ProceedingFactoryImpl();
    }

    @Bean
    public Factory<MeritsDecisionEntity, MeritsDecisionEntity.MeritsDecisionEntityBuilder> meritsDecisionFactory() {
        return new MeritsDecisionFactoryImpl();
    }

    @Bean
    public Factory<DecisionEntity, DecisionEntity.DecisionEntityBuilder> decisionFactoryFactory() {
        return new DecisionFactoryImpl();
    }

    @Bean
    public Factory<LinkedApplicationEntity, LinkedApplicationEntity.LinkedApplicationEntityBuilder> linkedApplicationEntityFactory() {
        return new LinkedApplicationEntityFactoryImpl();
    }
}