package uk.gov.justice.laa.dstew.access.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring configuration for the X-Authorization header JWT validation.
 *
 * <p>This configuration class — and all beans it declares — only exist in the Spring context
 * when {@code feature.x-authz=true}. When the flag is off, neither the decoder nor the filter
 * bean is created, and the filter is never registered in the security chain.
 */
@Configuration
@ConditionalOnProperty(prefix = "feature", name = "x-authz", havingValue = "true")
public class XAuthorizationConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    /**
     * A second {@link NimbusJwtDecoder} for the X-Authorization token.
     *
     * <p>Uses the same JWK set URI and issuer URI as the primary decoder (same Entra tenant).
     * No audience validator — the access token may be issued against any of several client
     * registrations.
     *
     * @return a configured JwtDecoder bean for X-Authorization tokens
     */
    @Bean("xAuthorizationJwtDecoder")
    public JwtDecoder xAuthorizationJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return decoder;
    }

    /**
     * Filter bean that validates the X-Authorization header and merges roles into the
     * existing {@link org.springframework.security.core.context.SecurityContext}.
     *
     * @param xAuthorizationJwtDecoder the decoder for the X-Authorization JWT
     * @return a configured {@link XAuthorizationFilter}
     */
    @Bean
    public OncePerRequestFilter xAuthorizationFilter(
            @Qualifier("xAuthorizationJwtDecoder") JwtDecoder xAuthorizationJwtDecoder) {
        return new XAuthorizationFilter(xAuthorizationJwtDecoder);
    }
}

