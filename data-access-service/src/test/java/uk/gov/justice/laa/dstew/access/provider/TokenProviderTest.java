package uk.gov.justice.laa.dstew.access.provider;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import uk.gov.justice.laa.dstew.access.exception.TokenProviderException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
class TokenProviderTest {

  @MockitoBean
  OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager;

  @Mock
  OAuth2AuthorizedClient oAuth2AuthorizedClient;

  @Mock
  OAuth2AccessToken oAuth2AccessToken;

  @Captor
  ArgumentCaptor<OAuth2AuthorizeRequest> oAuth2AuthorizedRequestCaptor;

  @Autowired
  TokenProvider tokenProvider;
  @Autowired
  private OAuth2AuthorizedClientManager authorizedClientManager;

  @Test
  void givenValidAuthorizedClient_whenGetTokenFromProvider_thenReturnAccessToken() {
    // Given
    when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(oAuth2AuthorizedClient);
    when(oAuth2AuthorizedClient.getAccessToken()).thenReturn(oAuth2AccessToken);

    // When
    OAuth2AccessToken accessToken = tokenProvider.getTokenFromProvider();

    // Then
    assertThat(accessToken).isEqualTo(oAuth2AccessToken);
  }

  @Test
  void givenNullAuthorizedClient_whenGetTokenFromProvider_thenThrowTokenProviderException() {
    assertThatExceptionOfType(TokenProviderException.class)
        .isThrownBy(() -> tokenProvider.getTokenFromProvider())
        .withMessage("Failed to obtain SDS API access token");
  }

  @Test
  void givenNullAccessToken_whenGetTokenFromProvider_thenThrowTokenProviderException() {
    // Given
    when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(oAuth2AuthorizedClient);

    // When & Then
    assertThatExceptionOfType(TokenProviderException.class)
        .isThrownBy(() -> tokenProvider.getTokenFromProvider())
        .withMessage("Failed to obtain SDS API access token");
  }

  @Test
  void givenClientAuthorizationException_whenGetTokenFromProvider_thenThrowTokenProviderException() {
    // Given
    when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class)))
        .thenThrow(new ClientAuthorizationException(new OAuth2Error("unauthorized_client"), "invalid token"));

    // When & Then
    assertThatExceptionOfType(TokenProviderException.class)
        .isThrownBy(() -> tokenProvider.getTokenFromProvider())
        .withMessageContaining("unauthorized_client");

    verify(authorizedClientManager).authorize(oAuth2AuthorizedRequestCaptor.capture());
  }

}