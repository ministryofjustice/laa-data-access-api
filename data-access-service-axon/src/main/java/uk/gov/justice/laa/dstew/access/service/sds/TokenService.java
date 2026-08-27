package uk.gov.justice.laa.dstew.access.service.sds;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.security.TokenProvider;

/** Service class for getting an SDS access token. */
@Service
@RequiredArgsConstructor
public class TokenService {

  private final TokenProvider tokenProvider;

  /**
   * Get the SDS API access token, evicting and refreshing if the cached token has expired.
   *
   * @return the access token value
   */
  public String getSdsAccessToken() {
    OAuth2AccessToken accessToken = tokenProvider.getTokenFromProvider();

    if (isValidToken(accessToken)) {
      return accessToken.getTokenValue();
    }

    tokenProvider.evictToken();
    return tokenProvider.getTokenFromProvider().getTokenValue();
  }

  private boolean isValidToken(OAuth2AccessToken accessToken) {
    return accessToken != null
        && accessToken.getExpiresAt() != null
        && accessToken.getExpiresAt().isAfter(Instant.now());
  }
}
