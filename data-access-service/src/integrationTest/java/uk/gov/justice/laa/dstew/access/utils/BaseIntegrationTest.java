package uk.gov.justice.laa.dstew.access.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.AccessApp;
import uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts.ApplicationAsserts;
import uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts.DomainEventAsserts;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.repository.DecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.LinkedApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.MeritsDecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;
import uk.gov.justice.laa.dstew.access.utils.factory.PersistedFactory;
import uk.gov.justice.laa.dstew.access.utils.generator.PersistedDataGenerator;

import java.net.URI;
import java.util.UUID;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(classes = AccessApp.class, properties = {"feature.disable-security=false"})
@ContextConfiguration(initializers = PostgresContainerInitializer.class)
@ExtendWith(SpringExtension.class)
@Transactional
public abstract class BaseIntegrationTest {

    @PersistenceContext
    protected EntityManager entityManager;

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    @Autowired
    protected PersistedDataGenerator persistedDataGenerator;

    @Autowired
    protected ApplicationRepository applicationRepository;

    @Autowired
    protected CaseworkerRepository caseworkerRepository;

    @Autowired
    protected DomainEventRepository domainEventRepository;

    @Autowired
    protected DecisionRepository decisionRepository;

    @Autowired
    protected LinkedApplicationRepository linkedApplicationRepository;

    @Autowired
    protected ApplicationAsserts applicationAsserts;

    @Autowired
    protected DomainEventAsserts domainEventAsserts;

    @Autowired
    protected Factory<ApplicationEntity, ApplicationEntity.ApplicationEntityBuilder> applicationFactory;

    @Autowired
    protected Factory<ApplicationCreateRequest, ApplicationCreateRequest.Builder> applicationCreateRequestFactory;

    @Autowired
    protected Factory<ApplicationUpdateRequest, ApplicationUpdateRequest.Builder> applicationUpdateRequestFactory;

    @Autowired
    protected Factory<CaseworkerAssignRequest, CaseworkerAssignRequest.Builder> caseworkerAssignRequestFactory;

    @Autowired
    protected Factory<CaseworkerUnassignRequest, CaseworkerUnassignRequest.Builder> caseworkerUnassignRequestFactory;

    @Autowired
    protected Factory<IndividualEntity, IndividualEntity.IndividualEntityBuilder> individualEntityFactory;

    @Autowired
    protected Factory<CaseworkerEntity, CaseworkerEntity.CaseworkerEntityBuilder> caseworkerFactory;

    @Autowired
    protected Factory<DomainEventEntity, DomainEventEntity.DomainEventEntityBuilder> domainEventFactory;

    @Autowired
    protected Factory<MakeDecisionRequest, MakeDecisionRequest.Builder> makeDecisionRequestFactory;

    @Autowired
    protected Factory<Individual, Individual.Builder> individualFactory;

    @Autowired
    protected Factory<LinkedApplicationEntity, LinkedApplicationEntity.LinkedApplicationEntityBuilder> linkedApplicationFactory;

    @Autowired
    protected PersistedFactory<
            ApplicationRepository,
            Factory<ApplicationEntity, ApplicationEntity.ApplicationEntityBuilder>,
            ApplicationEntity,
            ApplicationEntity.ApplicationEntityBuilder,
            UUID> persistedApplicationFactory;

    @Autowired
    protected PersistedFactory<
            CaseworkerRepository,
            Factory<CaseworkerEntity, CaseworkerEntity.CaseworkerEntityBuilder>,
            CaseworkerEntity,
            CaseworkerEntity.CaseworkerEntityBuilder,
            UUID> persistedCaseworkerFactory;

    @Autowired
    protected PersistedFactory<
              DomainEventRepository,
              Factory<DomainEventEntity, DomainEventEntity.DomainEventEntityBuilder>,
              DomainEventEntity,
              DomainEventEntity.DomainEventEntityBuilder,
              UUID> persistedDomainEventFactory;

    @Autowired
    protected PersistedFactory<
            ProceedingRepository,
            Factory<ProceedingEntity, ProceedingEntity.ProceedingEntityBuilder>,
            ProceedingEntity,
            ProceedingEntity.ProceedingEntityBuilder,
            UUID> persistedProceedingFactory;

    @Autowired
    protected PersistedFactory<
            MeritsDecisionRepository,
            Factory<MeritsDecisionEntity, MeritsDecisionEntity.MeritsDecisionEntityBuilder>,
            MeritsDecisionEntity,
            MeritsDecisionEntity.MeritsDecisionEntityBuilder,
            UUID> persistedMeritsDecisionFactory;

    @Autowired
    protected PersistedFactory<
            DecisionRepository,
            Factory<DecisionEntity, DecisionEntity.DecisionEntityBuilder>,
            DecisionEntity,
            DecisionEntity.DecisionEntityBuilder,
            UUID> persistedDecisionFactory;

    @Autowired
    protected PersistedFactory<
        LinkedApplicationRepository,
        Factory<LinkedApplicationEntity, LinkedApplicationEntity.LinkedApplicationEntityBuilder>,
        LinkedApplicationEntity,
        LinkedApplicationEntity.LinkedApplicationEntityBuilder,
        UUID
        > persistedLinkedApplicationFactory;

    // for use in tests and factories where applicable (i.e. default in ApplicationFactoryImpl)
    public static CaseworkerEntity CaseworkerJohnDoe;
    public static CaseworkerEntity CaseworkerJaneDoe;
    public static List<CaseworkerEntity> Caseworkers;

    @BeforeEach
    void setupCaseworkers() {
        caseworkerRepository.deleteAll();
        CaseworkerJohnDoe = persistedCaseworkerFactory.createAndPersist(builder ->
                builder.username("JohnDoe").build());
        CaseworkerJaneDoe = persistedCaseworkerFactory.createAndPersist(builder ->
                builder.username("JaneDoe").build());
        Caseworkers = List.of(CaseworkerJohnDoe, CaseworkerJaneDoe);

        entityManager.clear(); // ensure we clear the L1 cache before every test run
    }

    public MvcResult getUri(String uri) throws Exception {
        return mockMvc
            .perform(get(uri))
            .andReturn();
    }

    public MvcResult getUri(String uri, Object... args) throws Exception {
        return mockMvc
                .perform(get(uri, args))
                .andReturn();
    }

    public MvcResult getUri(URI uri) throws Exception {
        return mockMvc
                .perform(get(uri))
                .andReturn();
    }

    public MvcResult postUriWithoutModel(String uri, Object... args) throws Exception {
        return mockMvc
                .perform(post(uri, args))
                .andReturn();
    }

    public <TRequestModel> MvcResult postUri(String uri, TRequestModel requestModel) throws Exception {
        return mockMvc
                .perform(post(uri)
                        .content(objectMapper.writeValueAsString(requestModel))
                        .contentType(TestConstants.MediaTypes.APPLICATION_JSON))
                .andReturn();
    }

    public <TRequestModel> MvcResult postUri(String uri, TRequestModel requestModel, Object... args) throws Exception {
        return mockMvc
                .perform(post(uri, args)
                        .content(objectMapper.writeValueAsString(requestModel))
                        .contentType(TestConstants.MediaTypes.APPLICATION_JSON))
                .andReturn();
    }

    public <TRequestModel> MvcResult patchUri(String uri, TRequestModel requestModel, Object... args) throws Exception {
        return mockMvc
                .perform(patch(uri, args)
                        .content(objectMapper.writeValueAsString(requestModel))
                        .contentType(TestConstants.MediaTypes.APPLICATION_JSON))
                .andReturn();
    }

    public <TResponseModel> TResponseModel deserialise(MvcResult result, Class<TResponseModel> clazz) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), clazz);
    }
}
