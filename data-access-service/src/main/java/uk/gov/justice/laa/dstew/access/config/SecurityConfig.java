package uk.gov.justice.laa.dstew.access.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.azure.spring.cloud.autoconfigure.implementation.aad.security.AadResourceServerHttpSecurityConfigurer;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.SecurityFilterChain;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

/**
 * Spring Security configuration if security is not disabled.
 */
@ExcludeFromGeneratedCodeCoverage
@ConditionalOnProperty(prefix = "feature", name = "disable-security", havingValue = "false", matchIfMissing = true)
@Configuration
@EnableMethodSecurity
@EnableWebSecurity
public class SecurityConfig {

  @Value("${spring.security.oauth2.client.registration.moj-identity.client-id}")
  String clientId;
  @Value("${spring.security.oauth2.client.registration.moj-identity.client-secret}")
  String clientSecret;
  @Value("${spring.security.oauth2.client.registration.moj-identity.scope}")
  String scope;
  @Value("${spring.security.oauth2.client.provider.moj-identity.issuer-uri}")
  String issuerUri;
  @Value("${app.sds-api.client-registration-id}")
  private String clientRegistrationId;

  /**
   * Return the security filter chain.
   *
   * @param http Used to configure web security.
   * @return The built security configuration.
   * @throws Exception if anything went wrong.
   */

  @Bean
  SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
            .requestMatchers("/api/**").permitAll()
            .requestMatchers("/sds/**").permitAll()
            .anyRequest().authenticated())
         .with(AadResourceServerHttpSecurityConfigurer.aadResourceServer(), withDefaults())
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }

  /**
   * Effective authorization provider bean.
   *
   * @return the authorization provider
   */
  @Bean("entra")
  public EffectiveAuthorizationProvider authProvider() {
    return new EffectiveAuthorizationProvider() {
      @Override
      public boolean hasAppRole(String name) {
        return getAuthorities().contains("APPROLE_" + name);
      }

      @Override
      public boolean hasAnyAppRole(String... names) {
        final var authorities = getAuthorities();
        return Arrays.stream(names)
            .anyMatch(name -> authorities.contains("APPROLE_" + name));
      }

      private Set<String> getAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toUnmodifiableSet()) : Set.of();
      }
    };
  }

  /**
   * OAuth2AuthorizedClientManager bean for managing OAuth2 clients.
   *
   * @return the OAuth2AuthorizedClientManager
   */
  @Bean
  public OAuth2AuthorizedClientManager oauth2AuthorizedClientManager() {
    ClientRegistration identity = ClientRegistration.withRegistrationId(clientRegistrationId)
        .clientId(clientId)
        .clientSecret(clientSecret)
        .scope(scope)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .tokenUri(issuerUri)
        .build();

    ClientRegistrationRepository clientRegistrationRepository = new InMemoryClientRegistrationRepository(identity);

    OAuth2AuthorizedClientService clientService = new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);

    OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
        .clientCredentials()
        .build();

    AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, clientService);
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

    return authorizedClientManager;
  }
}
