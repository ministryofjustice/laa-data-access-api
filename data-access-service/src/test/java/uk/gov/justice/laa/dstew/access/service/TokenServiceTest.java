package uk.gov.justice.laa.dstew.access.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import uk.gov.justice.laa.dstew.access.provider.TokenProvider;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

  @Mock
  private TokenProvider tokenProvider;
  @Mock
  private OAuth2AccessToken accessToken;

  @InjectMocks
  private TokenService tokenService;

  @Test
  void givenValidToken_whenGetSdsAccessToken_thenReturnTokenValue() {
    // Given
    when(tokenProvider.getTokenFromProvider()).thenReturn(accessToken);
    when(accessToken.getExpiresAt()).thenReturn(Instant.now().plusSeconds(600));
    when(accessToken.getTokenValue()).thenReturn("access-token");

    // When
    String result = tokenService.getSdsAccessToken();

    // Then
    assertThat(result).isEqualTo("access-token");
    verify(tokenProvider, never()).evictToken();
  }

  @Test
  void givenExpiredToken_whenGetSdsAccessToken_thenEvictAndReturnNewTokenValue() {
    // Given
    when(tokenProvider.getTokenFromProvider()).thenReturn(accessToken);
    when(accessToken.getExpiresAt()).thenReturn(Instant.now().minusSeconds(1));
    when(accessToken.getTokenValue()).thenReturn("new-access-token");

    // When
    String result = tokenService.getSdsAccessToken();

    // Then
    assertThat(result).isEqualTo("new-access-token");
    verify(tokenProvider).evictToken();
  }

  @Test
  void givenNullToken_whenGetSdsAccessToken_thenEvictAndReturnNewTokenValue() {
    // Given
    when(tokenProvider.getTokenFromProvider()).thenReturn(null).thenReturn(accessToken);
    when(accessToken.getTokenValue()).thenReturn("new-access-token");

    // When
    String result = tokenService.getSdsAccessToken();

    // Then
    assertThat(result).isEqualTo("new-access-token");
    verify(tokenProvider).evictToken();
  }

  @Test
  void givenTokenWithNullExpiry_whenGetSdsAccessToken_thenEvictAndReturnNewTokenValue() {
    // Given
    when(tokenProvider.getTokenFromProvider()).thenReturn(accessToken);
    when(accessToken.getExpiresAt()).thenReturn(null);
    when(accessToken.getTokenValue()).thenReturn("new-access-token");

    // When
    String result = tokenService.getSdsAccessToken();

    // Then
    assertThat(result).isEqualTo("new-access-token");
    verify(tokenProvider).evictToken();
  }
}