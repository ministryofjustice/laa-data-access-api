package uk.gov.justice.laa.dstew.access.context;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.MDC;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Thread-safe logging context that provides MDC utilities for structured logging. Correlation ID
 * management is now fully handled by Micrometer Tracing.
 *
 * <p>Micrometer automatically populates MDC with 'traceId' and 'spanId'. This class provides
 * additional MDC utilities for application-specific context.
 */
@ExcludeFromGeneratedCodeCoverage
public class LoggingContext {

  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
  // ...existing code...
  public static final String SERVICE_NAME = "serviceName";
  public static final String ENVIRONMENT = "environment";
  public static final String USER_ID = "userId";
  public static final String REQUEST_METHOD = "requestMethod";
  public static final String REQUEST_URI = "requestUri";
  public static final String STATUS_CODE = "statusCode";

  private LoggingContext() {
    // Utility class
  }

  /**
   * Gets the current correlation ID from MDC. Per LAA logging guardrails, this is a UUID7 (or UUID4
   * fallback).
   *
   * @return the correlation ID (UUID7), or null if not set
   */
  public static String getCorrelationId() {
    return MDC.get("correlationId");
  }

  /**
   * Sets a context value in the MDC.
   *
   * @param key the context key
   * @param value the context value
   */
  public static void set(String key, String value) {
    if (value != null) {
      MDC.put(key, value);
    }
  }

  /**
   * Gets a context value from the MDC.
   *
   * @param key the context key
   * @return the context value, or null if not set
   */
  public static String get(String key) {
    return MDC.get(key);
  }

  /**
   * Removes a context value from the MDC.
   *
   * @param key the context key to remove
   */
  public static void remove(String key) {
    MDC.remove(key);
  }

  /**
   * Clears all context values from the MDC. Should be called at the end of request processing to
   * avoid context leaking to other requests in thread pools.
   */
  public static void clear() {
    MDC.clear();
  }

  /**
   * Gets all context values as a map.
   *
   * @return a copy of the current MDC context
   */
  public static Map<String, String> getAll() {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    return contextMap != null ? new HashMap<>(contextMap) : new HashMap<>();
  }

  /**
   * Sets the service name in the logging context.
   *
   * @param serviceName the service name
   */
  public static void setServiceName(String serviceName) {
    set(SERVICE_NAME, serviceName);
  }

  /**
   * Sets the environment in the logging context.
   *
   * @param environment the environment (e.g., dev, staging, prod)
   */
  public static void setEnvironment(String environment) {
    set(ENVIRONMENT, environment);
  }

  /**
   * Sets the user ID in the logging context.
   *
   * @param userId the user ID
   */
  public static void setUserId(String userId) {
    set(USER_ID, userId);
  }

  /**
   * Sets request-related information in the logging context.
   *
   * @param method the HTTP method
   * @param uri the request URI
   */
  public static void setRequestInfo(String method, String uri) {
    set(REQUEST_METHOD, method);
    set(REQUEST_URI, uri);
  }

  /**
   * Sets the HTTP status code in the logging context.
   *
   * @param statusCode the HTTP status code
   */
  public static void setStatusCode(int statusCode) {
    set(STATUS_CODE, String.valueOf(statusCode));
  }
}
