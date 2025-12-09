package uk.gov.justice.laa.dstew.access;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;
import uk.gov.justice.laa.dstew.access.utils.factory.PersistedFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.application.ApplicationCreateFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerFactoryImpl;
import uk.gov.justice.laa.dstew.access.utils.factory.individual.IndividualFactoryImpl;

import java.util.UUID;

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
    public Factory<ApplicationCreateRequest, ApplicationCreateRequest.Builder> applicationCreateRequestFactory() {
        return new ApplicationCreateFactoryImpl();
    }

    @Bean
    public Factory<IndividualEntity, IndividualEntity.IndividualEntityBuilder> individualFactory() {
        return new IndividualFactoryImpl();
    }

    @Bean
    public Factory<CaseworkerEntity, CaseworkerEntity.CaseworkerEntityBuilder> caseworkerFactory() {
        return new CaseworkerFactoryImpl();
    }
}