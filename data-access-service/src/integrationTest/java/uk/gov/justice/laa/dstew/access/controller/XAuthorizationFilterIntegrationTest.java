package uk.gov.justice.laa.dstew.access.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Date;
import java.util.List;
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
 * Integration tests for {@link uk.gov.justice.laa.dstew.access.config.XAuthorizationFilter}
 * when {@code feature.x-authz=true}.
 *
 * <p>Uses {@link EntraWireMock} to stub the JWK set and OIDC discovery endpoints so that both
 * {@code jwtDecoder} and {@code xAuthorizationJwtDecoder} resolve keys from a locally-generated
 * RSA pair — no Entra dependency.
 */
@SpringBootTest(classes = AccessApp.class, properties = {
    "feature.disable-security=false",
    "feature.x-authz=true",
    "spring.security.oauth2.resourceserver.jwt.audience=test-audience"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresContainerInitializer.class)
@ExtendWith(SpringExtension.class)
@Transactional
class XAuthorizationFilterIntegrationTest {

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
    void givenValidTokens_subMatch_hasRoles_thenOk() throws Exception {
        String oboToken = entra.signJwt("user-1", "test-audience", List.of("LAA_CASEWORKER"), false);
        String xAuthToken = entra.signJwt("user-1", null, List.of("LAA_CASEWORKER"), false);

        mockMvc.perform(get("/api/v0/applications")
                .header("Authorization", "Bearer " + oboToken)
                .header("X-Service-Name", "CIVIL_APPLY")
                .header("X-Authorization", xAuthToken))
            .andExpect(status().isOk());
    }

    @Test
    void givenXAuthorizationAbsent_then401() throws Exception {
        String oboToken = entra.signJwt("user-1", "test-audience", List.of("LAA_CASEWORKER"), false);

        mockMvc.perform(get("/api/v0/applications")
                .header("Authorization", "Bearer " + oboToken)
                .header("X-Service-Name", "CIVIL_APPLY"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void givenXAuthorizationExpired_then401() throws Exception {
        String oboToken = entra.signJwt("user-1", "test-audience", List.of("LAA_CASEWORKER"), false);
        String expiredXAuthToken = entra.signJwt("user-1", null, List.of("LAA_CASEWORKER"), true);

        mockMvc.perform(get("/api/v0/applications")
                .header("Authorization", "Bearer " + oboToken)
                .header("X-Service-Name", "CIVIL_APPLY")
                .header("X-Authorization", expiredXAuthToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void givenXAuthorizationWrongKey_then401() throws Exception {
        RSAKey differentKey = new RSAKeyGenerator(2048).keyID("different-key").generate();
        JWSSigner differentSigner = new RSASSASigner(differentKey);
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject("user-1")
            .issuer(entra.issuerUrl())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .build();
        SignedJWT differentJwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(differentKey.getKeyID()).build(),
            claims
        );
        differentJwt.sign(differentSigner);

        String oboToken = entra.signJwt("user-1", "test-audience", List.of("LAA_CASEWORKER"), false);

        mockMvc.perform(get("/api/v0/applications")
                .header("Authorization", "Bearer " + oboToken)
                .header("X-Service-Name", "CIVIL_APPLY")
                .header("X-Authorization", differentJwt.serialize()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void givenSubjectMismatch_then401() throws Exception {
        String oboToken = entra.signJwt("user-1", "test-audience", List.of("LAA_CASEWORKER"), false);
        String xAuthToken = entra.signJwt("user-2", null, List.of("LAA_CASEWORKER"), false);

        mockMvc.perform(get("/api/v0/applications")
                .header("Authorization", "Bearer " + oboToken)
                .header("X-Service-Name", "CIVIL_APPLY")
                .header("X-Authorization", xAuthToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void givenValidTokens_noRoleClaim_then403() throws Exception {
        String oboToken = entra.signJwt("user-1", "test-audience", null, false);
        String xAuthToken = entra.signJwt("user-1", null, null, false);

        mockMvc.perform(get("/api/v0/applications")
                .header("Authorization", "Bearer " + oboToken)
                .header("X-Service-Name", "CIVIL_APPLY")
                .header("X-Authorization", xAuthToken))
            .andExpect(status().isForbidden());
    }
}
