package uk.gov.justice.laa.dstew.access.security;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.exception.TokenProviderException;

/** Responsible for getting an access token from the SDS OAuth2 provider. */
@Component
@RequiredArgsConstructor
public class TokenProvider {

  @Value("${app.sds-api.client-registration-id}")
  private String clientRegistrationId;

  @Value("${app.sds-api.principal-name}")
  private String principalName;

  @Qualifier("sdsOauth2AuthorizedClientManager")
  private final OAuth2AuthorizedClientManager authorizedClientManager;

  /**
   * Get SDS API access token. Token caching and refresh are handled natively by {@link
   * OAuth2AuthorizedClientManager}.
   *
   * @return the access token
   */
  public OAuth2AccessToken getTokenFromProvider() {
    try {
      OAuth2AuthorizedClient authorizedClient =
          authorizedClientManager.authorize(buildAuthorizeRequest());

      if (Objects.isNull(authorizedClient)
          || Objects.requireNonNull(authorizedClient).getAccessToken() == null) {
        throw new TokenProviderException("Failed to obtain SDS API access token");
      }

      return authorizedClient.getAccessToken();
    } catch (ClientAuthorizationException clientAuthorizationException) {
      throw new TokenProviderException(clientAuthorizationException.getMessage());
    }
  }

  private OAuth2AuthorizeRequest buildAuthorizeRequest() {
    return OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
        .principal(principalName)
        .build();
  }
}
