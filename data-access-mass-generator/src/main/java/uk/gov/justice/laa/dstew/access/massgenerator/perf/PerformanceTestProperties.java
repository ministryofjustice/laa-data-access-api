package uk.gov.justice.laa.dstew.access.massgenerator.perf;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration for the API performance test runner.
 *
 * <p>All values can be overridden at runtime, e.g.:
 *
 * <pre>
 *   java -jar ... --spring.profiles.active=perf \
 *        --perf.base-url=https://staging.example.com \
 *        --perf.iterations=500 \
 *        --perf.concurrency=10 \
 *        --perf.bearer-token=eyJ...
 * </pre>
 */
@ConfigurationProperties(prefix = "perf")
public class PerformanceTestProperties {

  /** Base URL of the running API (no trailing slash). */
  private String baseUrl = "http://localhost:8080";

  /** Total number of create-application cycles to execute. */
  private int iterations = 100;

  /** Number of parallel threads (virtual users). */
  private int concurrency = 1;

  /** JWT bearer token sent in every request. */
  private String bearerToken = "";

  /** Value for the required X-Service-Name header. */
  private String xServiceName = "perf-test";

  /**
   * Fraction of created applications that will also trigger a make-decision call. Must be between
   * 0.0 and 1.0 inclusive.
   */
  private double decideRate = 0.5;

  /**
   * Minimum think time (ms) to sleep between the create and make-decision calls within a single
   * iteration. Models the time a caseworker spends reviewing the application before deciding.
   * Set to 0 to disable think time entirely (synthetic worst-case stress test).
   */
  private long thinkTimeMinMs = 0;

  /**
   * Maximum think time (ms). A random value between thinkTimeMinMs and thinkTimeMaxMs is chosen
   * per iteration. Defaults to the same as thinkTimeMinMs (i.e. fixed delay, not random).
   */
  private long thinkTimeMaxMs = 0;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public int getIterations() {
    return iterations;
  }

  public void setIterations(int iterations) {
    this.iterations = iterations;
  }

  public int getConcurrency() {
    return concurrency;
  }

  public void setConcurrency(int concurrency) {
    this.concurrency = concurrency;
  }

  public String getBearerToken() {
    return bearerToken;
  }

  public void setBearerToken(String bearerToken) {
    this.bearerToken = bearerToken;
  }

  public String getXServiceName() {
    return xServiceName;
  }

  public void setXServiceName(String xServiceName) {
    this.xServiceName = xServiceName;
  }

  public double getDecideRate() {
    return decideRate;
  }

  public void setDecideRate(double decideRate) {
    this.decideRate = decideRate;
  }

  public long getThinkTimeMinMs() {
    return thinkTimeMinMs;
  }

  public void setThinkTimeMinMs(long thinkTimeMinMs) {
    this.thinkTimeMinMs = thinkTimeMinMs;
  }

  public long getThinkTimeMaxMs() {
    return thinkTimeMaxMs;
  }

  public void setThinkTimeMaxMs(long thinkTimeMaxMs) {
    this.thinkTimeMaxMs = thinkTimeMaxMs;
  }
}
