package uk.gov.justice.laa.dstew.access.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

/**
 * Local security wrappers around the shared OAuth2 starter so Axon can keep starter-managed HTTP
 * security while preserving legacy method-security semantics and dev-token support.
 */
@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(prefix = "feature", name = "disable-security", havingValue = "false")
@ExcludeFromGeneratedCodeCoverage
public class SecurityConfig {

  private static final String AUTHORITY_PREFIX = "APPROLE_";
  private static final Map<String, List<String>> DEV_TOKENS =
      Map.of(
          "swagger-caseworker-token",
          List.of("APPROLE_LAA_CASEWORKER", "ROLE_LAA_CASEWORKER"),
          "unknown-token",
          List.of("APPROLE_UNKNOWN"));

  @Value("${spring.security.oauth2.resourceserver.jwt.audience}")
  private String audience;

  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
  private String issuerUri;

  @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
  private String jwkSetUri;

  @Value("${feature.enable-dev-token:false}")
  private boolean enableDevToken;

  /**
   * Provides a JWT decoder with issuer and audience validation for non-dev bearer tokens.
   *
   * @return the configured JWT decoder
   */
  @Bean
  @ConditionalOnMissingBean(JwtDecoder.class)
  public JwtDecoder jwtDecoder() {
    NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

    OAuth2TokenValidator<Jwt> audienceValidator =
        token -> {
          if (token.getAudience().contains(audience)) {
            return OAuth2TokenValidatorResult.success();
          }
          return OAuth2TokenValidatorResult.failure(
              new OAuth2Error("invalid_token", "The required audience is missing", null));
        };
    OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
    jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator));
    return jwtDecoder;
  }

  /**
   * Resolves authentication between the dev-token shortcut path and normal JWT validation.
   *
   * @param jwtDecoderProvider supplies the JWT decoder used for standard bearer tokens
   * @return the authentication manager resolver
   */
  @Bean
  AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver(
      ObjectProvider<JwtDecoder> jwtDecoderProvider) {
    AuthenticationManager jwtAuthenticationManager =
        jwtAuthenticationManager(jwtDecoderProvider.getIfAvailable(this::jwtDecoder));
    AuthenticationManager devTokenAuthenticationManager = this::authenticateDevToken;

    return request ->
        isDevTokenRequest(request) ? devTokenAuthenticationManager : jwtAuthenticationManager;
  }

  /**
   * Effective authorization provider bean.
   *
   * @return the authorization provider
   */
  @Bean("entra")
  public EffectiveAuthorizationProvider authProvider() {
    return new SecurityContextEffectiveAuthorizationProvider();
  }

  private AuthenticationManager jwtAuthenticationManager(JwtDecoder jwtDecoder) {
    JwtAuthenticationProvider authenticationProvider = new JwtAuthenticationProvider(jwtDecoder);
    authenticationProvider.setJwtAuthenticationConverter(jwtAuthenticationConverter());
    return authenticationProvider::authenticate;
  }

  private JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
        new JwtGrantedAuthoritiesConverter();
    grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
    grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");

    JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Set<GrantedAuthority> authorities =
              grantedAuthoritiesConverter.convert(jwt) == null
                  ? new java.util.HashSet<>()
                  : new java.util.HashSet<>(grantedAuthoritiesConverter.convert(jwt));
          authorities.add(new SimpleGrantedAuthority("APPROLE_LAA_CASEWORKER"));
          authorities.add(new SimpleGrantedAuthority("ROLE_LAA_CASEWORKER"));
          return authorities;
        });
    return jwtAuthenticationConverter;
  }

  private Authentication authenticateDevToken(Authentication authentication) {
    if (!(authentication instanceof BearerTokenAuthenticationToken bearerTokenAuthentication)) {
      return authentication;
    }

    List<String> roles = DEV_TOKENS.get(bearerTokenAuthentication.getToken());
    if (roles == null) {
      throw new org.springframework.security.oauth2.server.resource.InvalidBearerTokenException(
          "Invalid bearer token");
    }

    return new UsernamePasswordAuthenticationToken(
        "dev-user",
        bearerTokenAuthentication.getToken(),
        roles.stream().map(SimpleGrantedAuthority::new).toList());
  }

  private boolean isDevTokenRequest(HttpServletRequest request) {
    if (!enableDevToken) {
      return false;
    }

    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return false;
    }

    String token = authHeader.substring(7);
    return DEV_TOKENS.containsKey(token);
  }

  /** Gives methods to check the SecurityContext for roles and username. */
  @ExcludeFromGeneratedCodeCoverage
  private class SecurityContextEffectiveAuthorizationProvider
      implements EffectiveAuthorizationProvider {
    @Override
    public boolean hasAppRole(String name) {
      return getAuthorities().contains(AUTHORITY_PREFIX + name);
    }

    @Override
    public boolean hasAnyAppRole(String... names) {
      final var authorities = getAuthorities();
      return Arrays.stream(names).anyMatch(name -> authorities.contains(AUTHORITY_PREFIX + name));
    }

    @Override
    public boolean hasName() {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      return auth != null && auth.isAuthenticated() && !auth.getName().isBlank();
    }

    private Set<String> getAuthorities() {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      return (auth != null && auth.isAuthenticated())
          ? auth.getAuthorities().stream()
              .map(GrantedAuthority::getAuthority)
              .collect(Collectors.toUnmodifiableSet())
          : Set.of();
    }
  }
}
