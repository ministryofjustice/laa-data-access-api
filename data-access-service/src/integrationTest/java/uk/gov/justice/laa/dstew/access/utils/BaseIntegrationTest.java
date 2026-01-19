package uk.gov.justice.laa.dstew.access.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import uk.gov.justice.laa.dstew.access.entity.*;
import uk.gov.justice.laa.dstew.access.model.*;
import uk.gov.justice.laa.dstew.access.repository.*;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;
import uk.gov.justice.laa.dstew.access.utils.factory.PersistedFactory;

import java.net.URI;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(classes = AccessApp.class, properties = {"feature.disable-security=false"})
@ContextConfiguration(initializers = PostgresContainerInitializer.class)
@ExtendWith(SpringExtension.class)
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;


    @Autowired
    protected ApplicationRepository applicationRepository;

    @Autowired
    protected CaseworkerRepository caseworkerRepository;

    @Autowired
    protected DomainEventRepository domainEventRepository;

    @Autowired
    protected DecisionRepository decisionRepository;

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
    protected Factory<AssignDecisionRequest, AssignDecisionRequest.Builder> assignDecisionRequestFactory;

    @Autowired
    protected Factory<Individual, Individual.Builder> individualFactory;


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
