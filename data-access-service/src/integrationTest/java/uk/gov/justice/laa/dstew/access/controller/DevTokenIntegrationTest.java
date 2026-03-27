package uk.gov.justice.laa.dstew.access.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.dstew.access.AccessApp;
import uk.gov.justice.laa.dstew.access.utils.EntraWireMock;
import uk.gov.justice.laa.dstew.access.utils.PostgresContainerInitializer;

/**
 * Integration tests for the dev token flow with {@code feature.enable-dev-token=true}
 * and {@code feature.x-authz=false}.
 *
 * <p>Dev tokens are fixed opaque strings defined in {@link uk.gov.justice.laa.dstew.access.config.DevTokenConfig}.
 * When a recognised dev token is sent as a Bearer token:
 * <ul>
 *   <li>{@link uk.gov.justice.laa.dstew.access.config.DevBearerTokenResolverConfig} prevents the
 *       JWT filter from seeing the token string (returns {@code null}), so no JWT validation occurs.</li>
 *   <li>{@link uk.gov.justice.laa.dstew.access.config.DevTokenConfig#devTokenFilter()} intercepts
 *       the request before {@code BearerTokenAuthenticationFilter} and injects a fully-populated
 *       {@code Authentication} with the configured {@code APPROLE_*} authorities.</li>
 * </ul>
 *
 * <p>{@link EntraWireMock} is present so the application context can boot with valid OAuth2
 * issuer/JWK config. The dev token tests themselves never trigger JWT decoding.
 */
@SpringBootTest(classes = AccessApp.class, properties = {
    "feature.disable-security=false",
    "feature.enable-dev-token=true",
    "feature.x-authz=false",
    "spring.security.oauth2.resourceserver.jwt.audience=test-audience"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresContainerInitializer.class)
@ExtendWith(SpringExtension.class)
@Transactional
class DevTokenIntegrationTest {

    /** The token string recognised by {@code DevTokenConfig}. */
    private static final String CASEWORKER_DEV_TOKEN = "swagger-caseworker-token";

    @RegisterExtension
    static EntraWireMock entra = EntraWireMock.build();

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void entraProperties(DynamicPropertyRegistry registry) {
        entra.registerProperties(registry);
    }

    @BeforeEach
    void stubEntra() throws Exception {
        entra.stubEntraEndpoints();
    }

    @Test
    void givenCaseworkerDevToken_thenOk() throws Exception {
        mockMvc.perform(get("/api/v0/applications")
                .header("Authorization", "Bearer " + CASEWORKER_DEV_TOKEN)
                .header("X-Service-Name", "CIVIL_APPLY"))
            .andExpect(status().isOk());
    }

    @Test
    void givenCaseworkerDevToken_andXAuthorizationPresent_thenXAuthorizationIsIgnored() throws Exception {
        // x-authz=false so XAuthorizationFilter is not registered — the header has no effect
        mockMvc.perform(get("/api/v0/applications")
                .header("Authorization", "Bearer " + CASEWORKER_DEV_TOKEN)
                .header("X-Service-Name", "CIVIL_APPLY")
                .header("X-Authorization", "not-a-valid-jwt"))
            .andExpect(status().isOk());
    }

    @Test
    void givenUnrecognisedDevToken_then401() throws Exception {
        // An unknown string is not in DEV_TOKENS — DevBearerTokenResolverConfig returns it as a
        // real bearer token, JWT validation fails because it is not a valid JWT, so 401.
        mockMvc.perform(get("/api/v0/applications")
                .header("Authorization", "Bearer unknown-dev-token")
                .header("X-Service-Name", "CIVIL_APPLY"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void givenNoAuthorizationHeader_then401() throws Exception {
        mockMvc.perform(get("/api/v0/applications")
                .header("X-Service-Name", "CIVIL_APPLY"))
            .andExpect(status().isUnauthorized());
    }
}
