package uk.gov.justice.laa.dstew.access.service.sds;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import uk.gov.justice.laa.dstew.access.security.TokenProvider;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

  @Mock private TokenProvider tokenProvider;
  @Mock private OAuth2AccessToken accessToken;

  @InjectMocks private TokenService tokenService;

  @Test
  void givenValidToken_whenGetSdsAccessToken_thenReturnTokenValue() {
    when(tokenProvider.getTokenFromProvider()).thenReturn(accessToken);
    when(accessToken.getTokenValue()).thenReturn("access-token");

    String result = tokenService.getSdsAccessToken();

    assertThat(result).isEqualTo("access-token");
  }
}
