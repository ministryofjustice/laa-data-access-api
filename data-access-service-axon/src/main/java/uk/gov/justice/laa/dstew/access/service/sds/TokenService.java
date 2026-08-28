package uk.gov.justice.laa.dstew.access.service.sds;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.security.TokenProvider;

/** Service class for getting an SDS access token. */
@Service
@RequiredArgsConstructor
public class TokenService {

  private final TokenProvider tokenProvider;

  /**
   * Get the SDS API access token. Token caching and expiry are handled by the underlying {@link
   * OAuth2AuthorizedClientManager}.
   *
   * @return the access token value
   */
  public String getSdsAccessToken() {
    return tokenProvider.getTokenFromProvider().getTokenValue();
  }
}
