package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.justice.laa.dstew.access.exception.TokenProviderException;

@ExtendWith(MockitoExtension.class)
class TokenProviderTest {

  @Mock OAuth2AuthorizedClientManager authorizedClientManager;

  @Mock OAuth2AuthorizedClient oAuth2AuthorizedClient;

  @Mock OAuth2AccessToken oAuth2AccessToken;

  @Captor ArgumentCaptor<OAuth2AuthorizeRequest> oAuth2AuthorizedRequestCaptor;

  @InjectMocks TokenProvider tokenProvider;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(tokenProvider, "clientRegistrationId", "test-sds-client-id");
    ReflectionTestUtils.setField(tokenProvider, "principalName", "test-principal");
  }

  @Test
  void givenValidAuthorizedClient_whenGetTokenFromProvider_thenReturnAccessToken() {
    // Given
    when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class)))
        .thenReturn(oAuth2AuthorizedClient);
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
    when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class)))
        .thenReturn(oAuth2AuthorizedClient);

    // When & Then
    assertThatExceptionOfType(TokenProviderException.class)
        .isThrownBy(() -> tokenProvider.getTokenFromProvider())
        .withMessage("Failed to obtain SDS API access token");
  }

  @Test
  void
      givenClientAuthorizationException_whenGetTokenFromProvider_thenThrowTokenProviderException() {
    // Given
    when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class)))
        .thenThrow(
            new ClientAuthorizationException(
                new OAuth2Error("unauthorized_client"), "invalid token"));

    // When & Then
    assertThatExceptionOfType(TokenProviderException.class)
        .isThrownBy(() -> tokenProvider.getTokenFromProvider())
        .withMessageContaining("unauthorized_client");

    verify(authorizedClientManager).authorize(oAuth2AuthorizedRequestCaptor.capture());
  }
}
