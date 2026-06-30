package uk.gov.justice.laa.dstew.access.context;

import com.fasterxml.uuid.Generators;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Thread-safe logging context that manages correlation IDs and other contextual information for
 * structured logging. Uses SLF4J's MDC (Mapped Diagnostic Context) to propagate context across log
 * entries.
 */
@Slf4j
public class LoggingContext {

  public static final String CORRELATION_ID = "correlationId";
  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
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
   * Sets the correlation ID in the logging context. If correlationId is null, generates a new
   * UUID7.
   *
   * @param correlationId the correlation ID to set, or null to generate a new one
   * @return the correlation ID that was set
   */
  public static String setCorrelationId(String correlationId) {
    String id = correlationId != null ? correlationId : generateCorrelationId();
    MDC.put(CORRELATION_ID, id);
    return id;
  }

  /**
   * Gets the current correlation ID from the logging context.
   *
   * @return the correlation ID, or null if not set
   */
  public static String getCorrelationId() {
    return MDC.get(CORRELATION_ID);
  }

  /**
   * Generates a new UUID7-based correlation ID. Falls back to UUID4 if UUID7 is not available.
   *
   * @return a new correlation ID
   */
  public static String generateCorrelationId() {
    try {
      // Use UUID version 7 (time-ordered) for better sorting and indexing
      UUID uuid = Generators.timeBasedEpochGenerator().generate();
      return uuid.toString();
    } catch (Exception e) {
      log.warn("Failed to generate UUID7, falling back to UUID4", e);
      return UUID.randomUUID().toString();
    }
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
