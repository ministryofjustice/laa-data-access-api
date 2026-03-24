package uk.gov.justice.laa.dstew.access.utils.harness;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts.ApplicationAsserts;
import uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts.DomainEventAsserts;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.builders.HttpHeadersBuilder;
import uk.gov.justice.laa.dstew.access.utils.generator.PersistedDataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@ExtendWith(HarnessExtension.class)
public abstract class BaseHarnessTest {

    @HarnessInject
    protected WebTestClient webTestClient;

    @HarnessInject
    protected TestContextProvider harnessProvider;

    // Lazily resolved from harnessProvider in @BeforeEach
    protected ObjectMapper objectMapper;
    protected PersistedDataGenerator persistedDataGenerator;
    protected ApplicationRepository applicationRepository;
    protected CaseworkerRepository caseworkerRepository;
    protected DomainEventRepository domainEventRepository;
    protected DomainEventAsserts domainEventAsserts;
    protected ApplicationAsserts applicationAsserts;

    // Mirror BaseIntegrationTest field names so test code compiles unchanged.
    protected CaseworkerEntity CaseworkerJohnDoe;
    protected CaseworkerEntity CaseworkerJaneDoe;
    protected List<CaseworkerEntity> Caseworkers;

    private String currentToken = TestConstants.Tokens.CASEWORKER;
    private boolean omitToken = false;

    @BeforeEach
    void setupHarness() {
        objectMapper           = harnessProvider.getBean(ObjectMapper.class);
        persistedDataGenerator = harnessProvider.getBean(PersistedDataGenerator.class);
        applicationRepository  = harnessProvider.getBean(ApplicationRepository.class);
        caseworkerRepository   = harnessProvider.getBean(CaseworkerRepository.class);
        domainEventRepository  = harnessProvider.getBean(DomainEventRepository.class);
        domainEventAsserts     = harnessProvider.getBean(DomainEventAsserts.class);
        applicationAsserts     = harnessProvider.getBean(ApplicationAsserts.class);

        currentToken = TestConstants.Tokens.CASEWORKER;
        omitToken = false;


        CaseworkerJohnDoe = persistedDataGenerator.createAndPersist(
                CaseworkerGenerator.class, b -> b.username("JohnDoe").build());
        CaseworkerJaneDoe = persistedDataGenerator.createAndPersist(
                CaseworkerGenerator.class, b -> b.username("JaneDoe").build());
        Caseworkers = List.of(CaseworkerJohnDoe, CaseworkerJaneDoe);
    }

    @AfterEach
    void tearDownCaseworkers() {
        if (caseworkerRepository != null) {
            caseworkerRepository.deleteAll(Caseworkers);
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

        EntityExchangeResult<byte[]> raw = spec.exchange()
                .expectBody().returnResult();

        int status = raw.getStatus().value();
        Map<String, String> responseHeaders = raw.getResponseHeaders().toSingleValueMap();
        String body = raw.getResponseBody() == null
                ? "" : new String(raw.getResponseBody(), StandardCharsets.UTF_8);

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
     * GET uri with path-variable args and the supplied headers.
     * Mirrors BaseIntegrationTest.getUri(String, HttpHeaders, Object...).
     */
    public HarnessResult getUri(String uri, HttpHeaders headers, Object... args) {
        String expandedUri = UriComponentsBuilder.fromUriString(uri)
                .buildAndExpand(args)
                .toUriString();
        return getUri(expandedUri, headers);
    }

    /** Mirrors BaseIntegrationTest.deserialise(). */
    public <T> T deserialise(HarnessResult result, Class<T> clazz) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), clazz);
    }

    /** POST with body serialised to JSON and default CIVIL_APPLY service-name header. */
    public <T> HarnessResult postUri(String uri, T requestModel) throws Exception {
        return postUri(uri, requestModel, defaultServiceNameHeader());
    }

    /**
     * POST with body serialised to JSON and the supplied headers.
     * If {@code requestModel} is already a {@link String} it is used as-is (raw body).
     */
    public <T> HarnessResult postUri(String uri, T requestModel, HttpHeaders headers) throws Exception {
        String body = (requestModel instanceof String s)
                ? s
                : objectMapper.writeValueAsString(requestModel);

        WebTestClient.RequestBodySpec spec = webTestClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON);

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

        EntityExchangeResult<byte[]> raw = spec.bodyValue(body)
                .exchange()
                .expectBody().returnResult();

        int status = raw.getStatus().value();
        Map<String, String> responseHeaders = raw.getResponseHeaders().toSingleValueMap();
        String responseBody = raw.getResponseBody() == null
                ? "" : new String(raw.getResponseBody(), StandardCharsets.UTF_8);

        return new HarnessResult(status, responseHeaders, responseBody);
    }

    public HttpHeaders ServiceNameHeader(String serviceName) {
        HttpHeadersBuilder headersBuilder = new HttpHeadersBuilder();
        return (serviceName == null) ? null : headersBuilder.withServiceName(serviceName).build();
    }

    private HttpHeaders defaultServiceNameHeader() {
        return new HttpHeadersBuilder().withServiceName("CIVIL_APPLY").build();
    }
}