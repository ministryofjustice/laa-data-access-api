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
 * Integration tests for the normal OBO (On-Behalf-Of) token flow where roles are sourced
 * directly from the {@code LAA_APP_ROLES} claim on the OBO token ({@code feature.x-authz=false}).
 *
 * <p>Verifies that:
 * <ul>
 *   <li>A valid OBO token containing {@code LAA_APP_ROLES} grants access.</li>
 *   <li>An OBO token missing {@code LAA_APP_ROLES} is rejected with 403.</li>
 *   <li>An expired OBO token is rejected with 401.</li>
 *   <li>An OBO token signed by an unknown key is rejected with 401.</li>
 *   <li>An {@code X-Authorization} header, if present, is silently ignored.</li>
 * </ul>
 */
@SpringBootTest(classes = AccessApp.class, properties = {
    "feature.disable-security=false",
    "feature.x-authz=false",
    "spring.security.oauth2.resourceserver.jwt.audience=test-audience"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresContainerInitializer.class)
@ExtendWith(SpringExtension.class)
@Transactional
class OboTokenIntegrationTest {

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
    void givenOboTokenWithAppRoles_thenOk() throws Exception {
        String oboToken = entra.signJwt("user-1", "test-audience", List.of("LAA_CASEWORKER"), false);

        mockMvc.perform(get("/api/v0/applications")
                .header("Authorization", "Bearer " + oboToken)
                .header("X-Service-Name", "CIVIL_APPLY"))
            .andExpect(status().isOk());
    }

    @Test
    void givenOboTokenMissingAppRoles_then403() throws Exception {
        String oboToken = entra.signJwt("user-1", "test-audience", null, false);

        mockMvc.perform(get("/api/v0/applications")
                .header("Authorization", "Bearer " + oboToken)
                .header("X-Service-Name", "CIVIL_APPLY"))
            .andExpect(status().isForbidden());
    }

    @Test
    void givenOboTokenExpired_then401() throws Exception {
        String expiredToken = entra.signJwt("user-1", "test-audience", List.of("LAA_CASEWORKER"), true);

        mockMvc.perform(get("/api/v0/applications")
                .header("Authorization", "Bearer " + expiredToken)
                .header("X-Service-Name", "CIVIL_APPLY"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void givenOboTokenSignedByUnknownKey_then401() throws Exception {
        RSAKey unknownKey = new RSAKeyGenerator(2048).keyID("unknown-key").generate();
        JWSSigner unknownSigner = new RSASSASigner(unknownKey);
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject("user-1")
            .issuer(entra.issuerUrl())
            .audience("test-audience")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("LAA_APP_ROLES", List.of("LAA_CASEWORKER"))
            .build();
        SignedJWT unknownJwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(unknownKey.getKeyID()).build(),
            claims
        );
        unknownJwt.sign(unknownSigner);

        mockMvc.perform(get("/api/v0/applications")
                .header("Authorization", "Bearer " + unknownJwt.serialize())
                .header("X-Service-Name", "CIVIL_APPLY"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void givenOboTokenWithAppRoles_andXAuthorizationHeaderPresent_thenXAuthorizationIsIgnored() throws Exception {
        String oboToken = entra.signJwt("user-1", "test-audience", List.of("LAA_CASEWORKER"), false);

        mockMvc.perform(get("/api/v0/applications")
                .header("Authorization", "Bearer " + oboToken)
                .header("X-Service-Name", "CIVIL_APPLY")
                .header("X-Authorization", "not-a-valid-jwt"))
            .andExpect(status().isOk());
    }
}

