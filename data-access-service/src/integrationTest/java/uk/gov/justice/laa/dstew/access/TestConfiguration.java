package uk.gov.justice.laa.dstew.access;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.entity.*;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.AssignDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;
import uk.gov.justice.laa.dstew.access.utils.factory.PersistedFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationAssignDecisionFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationCreateFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationUpdateFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerAssignFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerUnassignFactoryImpl;
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
    public Factory<AssignDecisionRequest, AssignDecisionRequest.Builder> applicationAssignDecisionRequestFactory() {
        return new ApplicationAssignDecisionFactoryImpl();
    }

    @Bean
    public Factory<ProceedingEntity, ProceedingEntity.ProceedingEntityBuilder> proceedingFactory() {
        return new ProceedingFactoryImpl();
    }
}