package uk.gov.justice.laa.dstew.access.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

class RequestLoggingFilterConfigurationTest {

  private final CommonsRequestLoggingFilter filter =
      new RequestLoggingFilterConfiguration().logFilter();
  private final Logger logger = (Logger) LoggerFactory.getLogger(filter.getClass());
  private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
  private Level originalLevel;

  @BeforeEach
  void attachLogAppender() {
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    logEvents.start();
    logger.addAppender(logEvents);
  }

  @AfterEach
  void detachLogAppender() {
    logger.detachAppender(logEvents);
    logEvents.stop();
    logger.setLevel(originalLevel);
  }

  @Test
  void givenApplicationRequest_whenFiltered_thenLogsConfiguredRequestDetails() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/applications");
    request.setQueryString("page=1");
    request.setRemoteAddr("192.0.2.1");
    request.setRemoteUser("caller");
    request.addHeader("X-Test", "header-value");
    request.setContentType("text/plain");
    request.setCharacterEncoding(StandardCharsets.UTF_8.name());
    request.setContent("request-body".getBytes(StandardCharsets.UTF_8));
    AtomicBoolean chainInvoked = new AtomicBoolean();
    FilterChain chain =
        (filteredRequest, response) -> {
          chainInvoked.set(true);
          assertThat(filteredRequest.getInputStream().readAllBytes())
              .isEqualTo("request-body".getBytes(StandardCharsets.UTF_8));
        };

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chainInvoked).isTrue();
    assertThat(logEvents.list).hasSize(2);
    assertThat(logEvents.list.getLast().getFormattedMessage())
        .contains(
            "/applications?page=1",
            "client=192.0.2.1",
            "user=caller",
            "headers=",
            "X-Test",
            "header-value",
            "payload=request-body");
  }

  @ParameterizedTest
  @ValueSource(ints = {49_999, 50_000, 50_001})
  void givenPayload_whenFiltered_thenLogsAtMostFiftyThousandCharacters(int length)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/applications");
    request.setCharacterEncoding(StandardCharsets.UTF_8.name());
    request.setContent("a".repeat(length).getBytes(StandardCharsets.UTF_8));

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (filteredRequest, response) -> filteredRequest.getInputStream().readAllBytes());

    assertThat(logEvents.list).hasSize(2);
    String message = logEvents.list.getLast().getFormattedMessage();
    assertThat(message.substring(message.indexOf("payload=")))
        .isEqualTo("payload=" + "a".repeat(Math.min(length, 50_000)) + "]");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/actuator",
        "/actuator/health",
        "/swagger-ui",
        "/swagger-ui/index.html",
        "/v3/api-docs",
        "/v3/api-docs/swagger-config",
        "/api-docs",
        "/api-docs/example"
      })
  void givenInfrastructureUri_whenFiltered_thenSkipsLoggingButInvokesChain(String uri)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    AtomicBoolean chainInvoked = new AtomicBoolean();

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (filteredRequest, response) -> chainInvoked.set(true));

    assertThat(chainInvoked).isTrue();
    assertThat(logEvents.list).isEmpty();
  }

  @Test
  void givenNullUri_whenFiltered_thenLogsAndInvokesChain() throws Exception {
    MockHttpServletRequest request = spy(new MockHttpServletRequest());
    when(request.getRequestURI()).thenReturn(null);
    AtomicBoolean chainInvoked = new AtomicBoolean();

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (filteredRequest, response) -> chainInvoked.set(true));

    assertThat(chainInvoked).isTrue();
    assertThat(logEvents.list).hasSize(2);
    assertThat(logEvents.list.getFirst().getFormattedMessage()).contains("null");
  }
}
