package uk.gov.justice.laa.dstew.access.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Test utility that combines a {@link WireMockExtension} with a locally-generated RSA key pair
 * to simulate an Entra JWKS and OIDC discovery endpoint.
 *
 * <p>Intended to be declared as a {@code static} field on a test class using
 * {@link org.junit.jupiter.api.extension.RegisterExtension}:
 *
 * <pre>{@code
 * @RegisterExtension
 * static EntraWireMock entra = EntraWireMock.build();
 * }</pre>
 *
 * <p>Register the dynamic Spring properties with {@code @DynamicPropertySource}:
 *
 * <pre>{@code
 * @DynamicPropertySource
 * static void entraProperties(DynamicPropertyRegistry registry) {
 *     entra.registerProperties(registry);
 * }
 * }</pre>
 *
 * <p>Call {@link #stubEntraEndpoints()} in a {@code @BeforeEach} to register the WireMock stubs
 * for each test (WireMock clears stubs between tests by default):
 *
 * <pre>{@code
 * @BeforeEach
 * void stubEntra() throws Exception {
 *     entra.stubEntraEndpoints();
 * }
 * }</pre>
 *
 * <p>Sign test JWTs with {@link #signJwt(String, String, List, boolean)}.
 */
public class EntraWireMock extends WireMockExtension {

    private static final String ISSUER_PATH = "/issuer";
    private static final String JWKS_PATH = "/.well-known/jwks.json";
    private static final String OIDC_DISCOVERY_PATH = ISSUER_PATH + "/.well-known/openid-configuration";

    private final RSAKey rsaKey;

    private EntraWireMock(RSAKey rsaKey) {
        super(WireMockExtension.newInstance().options(
            com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort()
        ));
        this.rsaKey = rsaKey;
    }

    /**
     * Create a new {@code EntraWireMock} with a freshly-generated 2048-bit RSA key pair.
     *
     * @return a configured {@code EntraWireMock} ready to be used as a JUnit 5 extension
     */
    public static EntraWireMock build() {
        try {
            RSAKey key = new RSAKeyGenerator(2048).keyID("test-key").generate();
            return new EntraWireMock(key);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair for EntraWireMock", e);
        }
    }

    /**
     * Returns the issuer URL served by this WireMock instance.
     */
    public String issuerUrl() {
        return baseUrl() + ISSUER_PATH;
    }

    /**
     * Registers the OAuth2 Spring properties needed to point the application's JWT decoders at
     * this WireMock instance. Call from a {@code @DynamicPropertySource} method.
     */
    public void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> baseUrl() + JWKS_PATH);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
            () -> issuerUrl());
    }

    /**
     * Registers WireMock stubs for the JWKS and OIDC discovery endpoints.
     *
     * <p>Must be called in a {@code @BeforeEach} method because WireMock resets stubs between
     * tests when using {@link WireMockExtension}.
     */
    public void stubEntraEndpoints() throws Exception {
        stubFor(WireMock.get(urlEqualTo(JWKS_PATH))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(new JWKSet(rsaKey.toPublicJWK()).toString())));

        stubFor(WireMock.get(urlEqualTo(OIDC_DISCOVERY_PATH))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "issuer": "%s",
                      "jwks_uri": "%s%s"
                    }
                    """.formatted(issuerUrl(), baseUrl(), JWKS_PATH))));
    }

    /**
     * Returns the RSA key used to sign test JWTs. Use this when constructing a JWT that must
     * pass verification.
     * To fail verification sign a JWT with a different key
     */
    public RSAKey rsaKey() {
        return rsaKey;
    }

    /**
     * Signs a JWT with this instance's RSA key.
     *
     * @param sub      the {@code sub} (subject) claim
     * @param audience the {@code aud} claim — {@code null} to omit
     * @param appRoles the {@code LAA_APP_ROLES} claim — {@code null} to omit
     * @param expired  {@code true} to set {@code exp} 60 seconds in the past
     * @return the serialized signed JWT string
     */
    public String signJwt(String sub, String audience, List<String> appRoles, boolean expired)
            throws Exception {
        JWSSigner signer = new RSASSASigner(rsaKey);
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
            .subject(sub)
            .issuer(issuerUrl())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expired ? now.minusSeconds(60) : now.plusSeconds(300)));
        if (audience != null) {
            claims.audience(audience);
        }
        if (appRoles != null) {
            claims.claim("LAA_APP_ROLES", appRoles);
        }
        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
            claims.build()
        );
        jwt.sign(signer);
        return jwt.serialize();
    }
}

