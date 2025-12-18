package uk.gov.justice.laa.dstew.access.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.gov.justice.laa.dstew.access.AccessApp;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;
import uk.gov.justice.laa.dstew.access.utils.factory.PersistedFactory;

import java.net.URI;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = AccessApp.class)
@Transactional
public abstract class BaseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BaseIntegrationTest.class);

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;


    @Autowired
    protected ApplicationRepository applicationRepository;

    @Autowired
    protected CaseworkerRepository caseworkerRepository;

    @Autowired
    protected DomainEventRepository domainEventRepository;

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
    protected Factory<IndividualEntity, IndividualEntity.IndividualEntityBuilder> individualFactory;

    @Autowired
    protected Factory<CaseworkerEntity, CaseworkerEntity.CaseworkerEntityBuilder> caseworkerFactory;

    @Autowired
    protected Factory<DomainEventEntity, DomainEventEntity.DomainEventEntityBuilder> domainEventFactory;

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


    // for use in tests and factories where applicable (i.e. default in ApplicationFactoryImpl)
    public static CaseworkerEntity CaseworkerJohnDoe;
    public static CaseworkerEntity CaseworkerJaneDoe;

    @BeforeAll
    void setupCaseworkers() {
        CaseworkerJohnDoe = persistedCaseworkerFactory.createAndPersist(builder ->
                builder.username("JohnDoe").build());
        CaseworkerJaneDoe = persistedCaseworkerFactory.createAndPersist(builder ->
                builder.username("JaneDoe").build());
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