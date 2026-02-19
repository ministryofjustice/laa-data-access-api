package uk.gov.justice.laa.dstew.access.utils;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.AccessApp;
import uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts.ApplicationAsserts;
import uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts.DomainEventAsserts;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequestIndividual;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.repository.DecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.repository.IndividualRepository;
import uk.gov.justice.laa.dstew.access.repository.MeritsDecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.utils.builders.HttpHeadersBuilder;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;
import uk.gov.justice.laa.dstew.access.utils.factory.PersistedFactory;
import uk.gov.justice.laa.dstew.access.utils.generator.PersistedDataGenerator;

import java.net.URI;
import java.util.UUID;
import java.util.List;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(classes = AccessApp.class, properties = {"feature.disable-security=false"})
@ContextConfiguration(initializers = {PostgresContainerInitializer.class, LocalstackContainerInitializer.class})
@ExtendWith(SpringExtension.class)
@Transactional
public abstract class BaseIntegrationTest {
  @Autowired
  protected S3Client s3Client;
  @Autowired
  protected DynamoDbClient dynamoDbClient;
  @Autowired
  protected DynamoDbEnhancedClient dynamoDbEnhancedClient;
  static Logger log = Logger.getLogger(BaseIntegrationTest.class.getName());

  @Autowired
  @PersistenceContext
  protected EntityManager entityManager;

  @Autowired
  protected MockMvc mockMvc;
  @Autowired
  protected ObjectMapper objectMapper;

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
    protected CertificateRepository certificateRepository;

    @Autowired
    protected ApplicationAsserts applicationAsserts;

  @Autowired
  protected DomainEventAsserts domainEventAsserts;

    // for use in tests and factories where applicable (i.e. default in ApplicationFactoryImpl)
    public static CaseworkerEntity CaseworkerJohnDoe;
    public static CaseworkerEntity CaseworkerJaneDoe;
    public static List<CaseworkerEntity> Caseworkers;

    @BeforeEach
    void setupCaseworkers() {
        caseworkerRepository.deleteAll();
        CaseworkerJohnDoe = persistedDataGenerator.createAndPersist(CaseworkerGenerator.class, builder ->
                builder.username("JohnDoe").build());
        CaseworkerJaneDoe = persistedDataGenerator.createAndPersist(CaseworkerGenerator.class, builder ->
                builder.username("JaneDoe").build());
        Caseworkers = List.of(CaseworkerJohnDoe, CaseworkerJaneDoe);

    clearCache();
  }

  public HttpHeaders ServiceNameHeader(String serviceName) {
    HttpHeadersBuilder headersBuilder = new HttpHeadersBuilder();
    return (serviceName == null) ? null : headersBuilder.withServiceName(serviceName).build();
  }

  private HttpHeaders DefaultHttpHeaders() {
    HttpHeadersBuilder builder = new HttpHeadersBuilder();
    return builder.withServiceName("CIVIL_APPLY").build();
  }

  public MvcResult getUri(String uri, HttpHeaders httpHeaders) throws Exception {
    clearCache();

        MvcResult result;
        if (httpHeaders == null) {
          result = mockMvc
                    .perform(get(uri))
                    .andReturn();
        } else {
          result = mockMvc
              .perform(get(uri).headers(httpHeaders))
              .andReturn();
        }

        clearCache();
        return result;
    }

    public MvcResult getUri(String uri) throws Exception {
        return getUri(uri, DefaultHttpHeaders());
    }

  public MvcResult getUri(String uri, HttpHeaders httpHeaders, Object... args) throws Exception {
    clearCache();
    MvcResult result;

    if (httpHeaders == null) {
      result = mockMvc
          .perform(get(uri, args))
          .andReturn();
    } else {
      result = mockMvc
          .perform(get(uri, args).headers(httpHeaders))
          .andReturn();
    }
    clearCache();
    return result;
  }

  public MvcResult getUri(String uri, Object... args) throws Exception {
    return getUri(uri, DefaultHttpHeaders(), args);
  }

  public MvcResult getUri(URI uri, HttpHeaders httpHeaders) throws Exception {
    clearCache();
    MvcResult result;
    if (httpHeaders == null) {
      result = mockMvc
          .perform(get(uri))
          .andReturn();
    } else {
      result = mockMvc
          .perform(get(uri).headers(httpHeaders))
          .andReturn();

    }
    clearCache();
    return result;
  }

  public MvcResult getUri(URI uri) throws Exception {
    return getUri(uri, DefaultHttpHeaders());
  }

  public MvcResult postUriWithoutModel(String uri, HttpHeaders httpHeaders, Object... args) throws Exception {
    clearCache();
    MvcResult result;

    if (httpHeaders == null) {
      result = mockMvc
          .perform(post(uri, args))
          .andReturn();
    } else {
      result = mockMvc
          .perform(post(uri, args).headers(httpHeaders))
          .andReturn();
    }

    clearCache();
    return result;
  }

  public MvcResult postUriWithoutModel(String uri, Object... args) throws Exception {
    return postUriWithoutModel(uri, DefaultHttpHeaders(), args);
  }

  public <TRequestModel> MvcResult postUri(String uri, TRequestModel requestModel, HttpHeaders httpHeaders) throws Exception {
    clearCache();
    MvcResult result;

    if (httpHeaders == null) {
      result = mockMvc
          .perform(post(uri)
              .content(objectMapper.writeValueAsString(requestModel))
              .contentType(TestConstants.MediaTypes.APPLICATION_JSON))
          .andReturn();
    } else {
      result = mockMvc
          .perform(post(uri)
              .content(objectMapper.writeValueAsString(requestModel))
              .contentType(TestConstants.MediaTypes.APPLICATION_JSON)
              .headers(httpHeaders))
          .andReturn();
    }
    clearCache();
    return result;
  }

  public <TRequestModel> MvcResult postUri(String uri, TRequestModel requestModel) throws Exception {
    return postUri(uri, requestModel, DefaultHttpHeaders());
  }

  public <TRequestModel> MvcResult postUri(String uri, TRequestModel requestModel, HttpHeaders httpHeaders, Object... args)
      throws Exception {
    clearCache();
    MvcResult result;

    if (httpHeaders == null) {
      result = mockMvc
          .perform(post(uri, args)
              .content(objectMapper.writeValueAsString(requestModel))
              .contentType(TestConstants.MediaTypes.APPLICATION_JSON))
          .andReturn();
    } else {
      result = mockMvc
          .perform(post(uri, args)
              .content(objectMapper.writeValueAsString(requestModel))
              .contentType(TestConstants.MediaTypes.APPLICATION_JSON)
              .headers(httpHeaders))
          .andReturn();
    }
    clearCache();
    return result;
  }

  public <TRequestModel> MvcResult postUri(String uri, TRequestModel requestModel, Object... args) throws Exception {
    return postUri(uri, requestModel, DefaultHttpHeaders(), args);
  }

  public <TRequestModel> MvcResult patchUri(String uri, TRequestModel requestModel, HttpHeaders httpHeaders, Object... args)
      throws Exception {
    clearCache();
    MvcResult result;

    if (httpHeaders == null) {
      result = mockMvc
          .perform(patch(uri, args)
              .content(objectMapper.writeValueAsString(requestModel))
              .contentType(TestConstants.MediaTypes.APPLICATION_JSON))
          .andReturn();
    } else {
      result = mockMvc
          .perform(patch(uri, args)
              .content(objectMapper.writeValueAsString(requestModel))
              .contentType(TestConstants.MediaTypes.APPLICATION_JSON)
              .headers(httpHeaders))
          .andReturn();
    }
    clearCache();
    return result;
  }

  public <TRequestModel> MvcResult patchUri(String uri, TRequestModel requestModel, Object... args) throws Exception {
    return patchUri(uri, requestModel, DefaultHttpHeaders(), args);
  }

  public <TResponseModel> TResponseModel deserialise(MvcResult result, Class<TResponseModel> clazz) throws Exception {
    return objectMapper.readValue(result.getResponse().getContentAsString(), clazz);
  }

    public void clearCache() {
        entityManager.flush();
        entityManager.clear();
    }

    @Autowired
    protected Factory<ApplicationCreateRequestIndividual, ApplicationCreateRequestIndividual.Builder> applicationCreateRequestIndividualFactory;
}
