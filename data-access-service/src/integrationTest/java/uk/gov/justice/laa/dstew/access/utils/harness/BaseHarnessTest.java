package uk.gov.justice.laa.dstew.access.utils.harness;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts.ApplicationAsserts;
import uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts.DecisionAsserts;
import uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts.DomainEventAsserts;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.repository.NoteRepository;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.builders.HttpHeadersBuilder;
import uk.gov.justice.laa.dstew.access.utils.generator.PersistedDataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;

@ExtendWith(HarnessExtension.class)
public abstract class BaseHarnessTest {

  @HarnessInject protected WebTestClient webTestClient;

  @HarnessInject protected TestContextProvider harnessProvider;

  // Lazily resolved from harnessProvider in @BeforeEach
  protected ObjectMapper objectMapper;
  protected PersistedDataGenerator persistedDataGenerator;
  protected ApplicationRepository applicationRepository;
  protected CaseworkerRepository caseworkerRepository;
  protected DomainEventRepository domainEventRepository;
  protected CertificateRepository certificateRepository;
  protected NoteRepository noteRepository;
  protected DomainEventAsserts domainEventAsserts;
  protected ApplicationAsserts applicationAsserts;
  protected DecisionAsserts decisionAsserts;

  /**
   * Asserts that all application-domain tables are empty after each test's teardown. Resolved
   * lazily in @BeforeEach from the Spring context via harnessProvider.
   */
  protected DatabaseCleanlinessAssertion dbCleanliness;

  /**
   * Captures row-count snapshots and asserts parity between before/after snapshots. Used by
   * infrastructure-test sentinels to verify no data leaks across the suite. Resolved lazily
   * in @BeforeEach from the Spring context via harnessProvider.
   */
  protected TableRowCountAssertion tableRowCountAssertion;

  // Mirror BaseIntegrationTest field names so test code compiles unchanged.
  protected CaseworkerEntity CaseworkerJohnDoe;
  protected CaseworkerEntity CaseworkerJaneDoe;
  protected List<CaseworkerEntity> Caseworkers;

  /**
   * Returns the IDs of applications tracked in the current test. Delegates to
   * PersistedDataGenerator — useful for asserting that no application was persisted by a
   * bad-request scenario.
   */
  protected Set<UUID> trackedApplicationIds() {
    return persistedDataGenerator.trackedApplicationIds();
  }

  private String currentToken = TestConstants.Tokens.CASEWORKER;
  private boolean omitToken = false;

  @Order(1)
  @BeforeEach
  void setupHarness() {
    objectMapper = harnessProvider.getBean(ObjectMapper.class);
    persistedDataGenerator = harnessProvider.getBean(PersistedDataGenerator.class);
    applicationRepository = harnessProvider.getBean(ApplicationRepository.class);
    caseworkerRepository = harnessProvider.getBean(CaseworkerRepository.class);
    domainEventRepository = harnessProvider.getBean(DomainEventRepository.class);
    certificateRepository = harnessProvider.getBean(CertificateRepository.class);
    noteRepository = harnessProvider.getBean(NoteRepository.class);
    domainEventAsserts = harnessProvider.getBean(DomainEventAsserts.class);
    applicationAsserts = harnessProvider.getBean(ApplicationAsserts.class);
    decisionAsserts = harnessProvider.getBean(DecisionAsserts.class);
    dbCleanliness = harnessProvider.getBean(DatabaseCleanlinessAssertion.class);
    tableRowCountAssertion = harnessProvider.getBean(TableRowCountAssertion.class);

    currentToken = TestConstants.Tokens.CASEWORKER;
    omitToken = false;

    // Belt-and-braces: clear any IDs left over from a previous test's failed
    // teardown.  deleteTrackedData() guarantees clearTrackedIds() via try/finally,
    // so this should always be a no-op — but it is cheap and makes the invariant
    // explicit: tracking lists are empty before every test starts.
    persistedDataGenerator.clearTrackedIds();

    CaseworkerJohnDoe =
        persistedDataGenerator.createAndPersist(
            CaseworkerGenerator.class, b -> b.username("JohnDoe").build());
    CaseworkerJaneDoe =
        persistedDataGenerator.createAndPersist(
            CaseworkerGenerator.class, b -> b.username("JaneDoe").build());
    Caseworkers = List.of(CaseworkerJohnDoe, CaseworkerJaneDoe);
  }

  /**
   * Step 1: delete all rows that were tracked via PersistedDataGenerator. Ordered first so that the
   * cleanliness assertion (@Order(2)) always runs after tracked data has been removed.
   */
  @Order(1)
  @AfterEach
  protected void tearDownTrackedData() {
    if (persistedDataGenerator != null) {
      persistedDataGenerator.deleteTrackedData();
    }
  }

  /**
   * Step 2: assert that every application-domain table is empty. Runs after {@link
   * #tearDownTrackedData()} thanks to {@link Order @Order(2)}.
   *
   * <p>Skipped if {@code dbCleanliness} was never initialised (e.g. if {@link #setupHarness()}
   * threw before resolving the bean).
   */
  @Order(2)
  @AfterEach
  void assertDatabaseCleanAfterTest() {
    if (dbCleanliness != null && !HarnessMode.isInfrastructure()) {
      dbCleanliness.assertAllTablesEmpty(getClass().getSimpleName());
    }
  }

  // ── Auth helpers ──────────────────────────────────────────────────────────

  protected void withToken(String token) {
    this.currentToken = token;
    this.omitToken = false;
  }

  protected void withNoToken() {
    this.omitToken = true;
  }

  // ── HTTP helpers ──────────────────────────────────────────────────────────

  public HarnessResult getUri(String uri) {
    return getUri(uri, defaultServiceNameHeader());
  }

  public HarnessResult getUri(String uri, HttpHeaders headers) {
    WebTestClient.RequestHeadersSpec<?> spec = webTestClient.get().uri(uri);

    if (!omitToken) {
      spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + currentToken);
    }
    if (headers != null) {
      for (Map.Entry<String, List<String>> entry : headers.headerSet()) {
        spec = spec.header(entry.getKey(), entry.getValue().toArray(String[]::new));
      }
    }

    // Reset token state after each use
    currentToken = TestConstants.Tokens.CASEWORKER;
    omitToken = false;

    EntityExchangeResult<byte[]> raw = spec.exchange().expectBody().returnResult();

    int status = raw.getStatus().value();
    Map<String, String> responseHeaders = raw.getResponseHeaders().toSingleValueMap();
    String body =
        raw.getResponseBody() == null
            ? ""
            : new String(raw.getResponseBody(), StandardCharsets.UTF_8);

    return new HarnessResult(status, responseHeaders, body);
  }

  /**
   * GET uri with path-variable args, X-Service-Name: CIVIL_APPLY, and the current bearer token.
   * Mirrors BaseIntegrationTest.getUri(String, Object...).
   */
  public HarnessResult getUri(String uri, Object... args) {
    return getUri(uri, defaultServiceNameHeader(), args);
  }

  /**
   * GET uri with path-variable args and the supplied headers. Mirrors
   * BaseIntegrationTest.getUri(String, HttpHeaders, Object...).
   */
  public HarnessResult getUri(String uri, HttpHeaders headers, Object... args) {
    String expandedUri = UriComponentsBuilder.fromUriString(uri).buildAndExpand(args).toUriString();
    return getUri(expandedUri, headers);
  }

  /** Mirrors BaseIntegrationTest.deserialise(). */
  public <T> T deserialise(HarnessResult result, Class<T> clazz) throws Exception {
    return objectMapper.readValue(result.getResponse().getContentAsString(), clazz);
  }

  public HarnessResult getUri(URI uri) {
    return getUri(uri.toString(), defaultServiceNameHeader());
  }

  public HarnessResult getUri(URI uri, HttpHeaders headers) {
    return getUri(uri.toString(), headers);
  }

  /** POST with body serialised to JSON, default CIVIL_APPLY header, and path-variable args. */
  public <T> HarnessResult postUri(String uri, T requestModel) throws Exception {
    return postUri(uri, requestModel, defaultServiceNameHeader());
  }

  /**
   * POST with body serialised to JSON and the supplied headers. If {@code requestModel} is already
   * a {@link String} it is used as-is (raw body).
   */
  public <T> HarnessResult postUri(String uri, T requestModel, HttpHeaders headers)
      throws Exception {
    String body =
        (requestModel instanceof String s) ? s : objectMapper.writeValueAsString(requestModel);

    WebTestClient.RequestBodySpec spec =
        webTestClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON);

    if (!omitToken) {
      spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + currentToken);
    }
    if (headers != null) {
      for (Map.Entry<String, List<String>> entry : headers.headerSet()) {
        spec = spec.header(entry.getKey(), entry.getValue().toArray(String[]::new));
      }
    }

    // Reset token state after each use
    currentToken = TestConstants.Tokens.CASEWORKER;
    omitToken = false;

    EntityExchangeResult<byte[]> raw = spec.bodyValue(body).exchange().expectBody().returnResult();

    int status = raw.getStatus().value();
    Map<String, String> responseHeaders = raw.getResponseHeaders().toSingleValueMap();
    String responseBody =
        raw.getResponseBody() == null
            ? ""
            : new String(raw.getResponseBody(), StandardCharsets.UTF_8);

    return new HarnessResult(status, responseHeaders, responseBody);
  }

  /** POST with body serialised to JSON, default CIVIL_APPLY header, and path-variable args. */
  public <T> HarnessResult postUri(String uri, T requestModel, Object... args) throws Exception {
    return postUri(uri, requestModel, defaultServiceNameHeader(), args);
  }

  /** POST with body serialised to JSON, the supplied headers, and path-variable args. */
  public <T> HarnessResult postUri(String uri, T requestModel, HttpHeaders headers, Object... args)
      throws Exception {
    String expandedUri = UriComponentsBuilder.fromUriString(uri).buildAndExpand(args).toUriString();
    return postUri(expandedUri, requestModel, headers);
  }

  public HttpHeaders ServiceNameHeader(String serviceName) {
    HttpHeadersBuilder headersBuilder = new HttpHeadersBuilder();
    return (serviceName == null) ? null : headersBuilder.withServiceName(serviceName).build();
  }

  private HttpHeaders defaultServiceNameHeader() {
    return new HttpHeadersBuilder().withServiceName("CIVIL_APPLY").build();
  }

  /**
   * PATCH with body serialised to JSON, default CIVIL_APPLY service-name header, and path-variable
   * args.
   */
  public <T> HarnessResult patchUri(String uri, T requestModel, Object... args) throws Exception {
    return patchUri(uri, requestModel, defaultServiceNameHeader(), args);
  }

  /**
   * PATCH with body serialised to JSON, the supplied headers, and path-variable args. Mirrors
   * BaseIntegrationTest.patchUri(String, TRequestModel, HttpHeaders, Object...).
   */
  public <T> HarnessResult patchUri(String uri, T requestModel, HttpHeaders headers, Object... args)
      throws Exception {
    String expandedUri = UriComponentsBuilder.fromUriString(uri).buildAndExpand(args).toUriString();

    String body = objectMapper.writeValueAsString(requestModel);

    WebTestClient.RequestBodySpec spec =
        webTestClient.patch().uri(expandedUri).contentType(MediaType.APPLICATION_JSON);

    if (!omitToken) {
      spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + currentToken);
    }
    if (headers != null) {
      for (Map.Entry<String, List<String>> entry : headers.headerSet()) {
        spec = spec.header(entry.getKey(), entry.getValue().toArray(String[]::new));
      }
    }

    // Reset token state after each use
    currentToken = TestConstants.Tokens.CASEWORKER;
    omitToken = false;

    EntityExchangeResult<byte[]> raw = spec.bodyValue(body).exchange().expectBody().returnResult();

    int status = raw.getStatus().value();
    Map<String, String> responseHeaders = raw.getResponseHeaders().toSingleValueMap();
    String responseBody =
        raw.getResponseBody() == null
            ? ""
            : new String(raw.getResponseBody(), StandardCharsets.UTF_8);

    return new HarnessResult(status, responseHeaders, responseBody);
  }
}
