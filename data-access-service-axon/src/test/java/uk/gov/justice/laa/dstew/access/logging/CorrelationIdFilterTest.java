package uk.gov.justice.laa.dstew.access.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  @Test
  void givenNoCorrelationIdHeader_whenFiltered_thenGeneratedIdIsAvailableInMdc() throws Exception {
    Tracer tracer = mock(Tracer.class);
    when(tracer.createBaggageInScope(eq("correlationId"), anyString()))
        .thenReturn(mock(BaggageInScope.class));
    CorrelationIdFilter filter = new CorrelationIdFilter(tracer);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(
        request,
        response,
        (servletRequest, servletResponse) ->
            assertThat(LoggingContext.getCorrelationId())
                .isEqualTo(response.getHeader(LoggingContext.CORRELATION_ID_HEADER)));

    assertThat(response.getHeader(LoggingContext.CORRELATION_ID_HEADER)).isNotBlank();
    assertThat(LoggingContext.getCorrelationId()).isNull();
  }
}
