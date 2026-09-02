package uk.gov.justice.laa.dstew.access.service.sds;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import uk.gov.justice.laa.dstew.access.exception.FileLengthRequiredException;
import uk.gov.justice.laa.dstew.access.exception.VirusDetectedException;
import uk.gov.justice.laa.dstew.access.exception.VirusScanException;

@ExtendWith(MockitoExtension.class)
class SdsUploadResponseHandlerTest {

  @InjectMocks private SdsUploadResponseHandler handler;

  @SuppressWarnings("unchecked")
  @Test
  void givenResponseSpec_whenHandle_thenAppliesCorrectPredicatesAndHandlers() throws Exception {
    RestClient.ResponseSpec spec = mock(RestClient.ResponseSpec.class);
    ArgumentCaptor<Predicate<HttpStatusCode>> predicateCaptor =
        ArgumentCaptor.forClass(Predicate.class);
    ArgumentCaptor<RestClient.ResponseSpec.ErrorHandler> handlerCaptor =
        ArgumentCaptor.forClass(RestClient.ResponseSpec.ErrorHandler.class);
    when(spec.onStatus(predicateCaptor.capture(), handlerCaptor.capture())).thenReturn(spec);

    handler.handle(spec);

    verify(spec, times(3)).onStatus(any(), any());

    List<Predicate<HttpStatusCode>> predicates = predicateCaptor.getAllValues();
    List<RestClient.ResponseSpec.ErrorHandler> handlers = handlerCaptor.getAllValues();

    // Predicate 0: LENGTH_REQUIRED (411)
    assertThat(predicates.get(0).test(HttpStatus.LENGTH_REQUIRED)).isTrue();
    assertThat(predicates.get(0).test(HttpStatus.OK)).isFalse();

    // Predicate 1: 422 Unprocessable Entity
    assertThat(predicates.get(1).test(HttpStatus.UNPROCESSABLE_ENTITY)).isTrue();
    assertThat(predicates.get(1).test(HttpStatus.OK)).isFalse();

    // Predicate 2: catch-all (not 2xx, not 409, not 411, not 422)
    assertThat(predicates.get(2).test(HttpStatus.INTERNAL_SERVER_ERROR)).isTrue();
    assertThat(predicates.get(2).test(HttpStatus.OK)).isFalse();
    assertThat(predicates.get(2).test(HttpStatus.CONFLICT)).isFalse();
    assertThat(predicates.get(2).test(HttpStatus.LENGTH_REQUIRED)).isFalse();
    assertThat(predicates.get(2).test(HttpStatus.UNPROCESSABLE_ENTITY)).isFalse();

    // Handler 0: FileLengthRequiredException
    assertThatExceptionOfType(FileLengthRequiredException.class)
        .isThrownBy(() -> handlers.get(0).handle(null, null));

    // Handler 1: VirusDetectedException
    assertThatExceptionOfType(VirusDetectedException.class)
        .isThrownBy(() -> handlers.get(1).handle(null, null));

    // Handler 2: VirusScanException (uses res.getStatusCode())
    ClientHttpResponse mockResponse = mock(ClientHttpResponse.class);
    doReturn(HttpStatus.INTERNAL_SERVER_ERROR).when(mockResponse).getStatusCode();
    assertThatExceptionOfType(VirusScanException.class)
        .isThrownBy(() -> handlers.get(2).handle(null, mockResponse));
  }
}
