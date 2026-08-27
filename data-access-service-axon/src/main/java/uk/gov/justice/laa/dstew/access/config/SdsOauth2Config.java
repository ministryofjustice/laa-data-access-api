package uk.gov.justice.laa.dstew.access.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Configures the OAuth2 client used to acquire tokens for outbound SDS API calls. */
@Configuration
@ExcludeFromGeneratedCodeCoverage
public class SdsOauth2Config {

  @Value("${app.sds-api.client-registration-id}")
  private String clientRegistrationId;

  @Value("${spring.security.oauth2.client.registration.moj-identity.client-id}")
  private String clientId;

  @Value("${spring.security.oauth2.client.registration.moj-identity.client-secret}")
  private String clientSecret;

  @Value("${spring.security.oauth2.client.registration.moj-identity.scope}")
  private String scope;

  @Value("${spring.security.oauth2.client.provider.moj-identity.token-uri}")
  private String tokenUri;

  /**
   * Provides an {@link OAuth2AuthorizedClientManager} configured for SDS client-credentials flow.
   * This is intentionally isolated from the resource-server security configuration so that inbound
   * JWT validation and outbound SDS token acquisition remain fully independent.
   *
   * @return the configured OAuth2AuthorizedClientManager
   */
  @Bean("sdsOauth2AuthorizedClientManager")
  public OAuth2AuthorizedClientManager sdsOauth2AuthorizedClientManager() {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId(clientRegistrationId)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .scope(scope)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri(tokenUri)
            .build();

    ClientRegistrationRepository clientRegistrationRepository =
        new InMemoryClientRegistrationRepository(registration);

    OAuth2AuthorizedClientService clientService =
        new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);

    OAuth2AuthorizedClientProvider authorizedClientProvider =
        OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build();

    AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrationRepository, clientService);
    manager.setAuthorizedClientProvider(authorizedClientProvider);

    return manager;
  }
}
