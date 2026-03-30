package uk.gov.justice.laa.dstew.access.config;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.metrics.QueryMetricsCollector;

/**
 * Configuration for DataSource Proxy to capture SQL query metrics.
 *
 * <p>Uses a BeanPostProcessor approach to wrap the DataSource after it's created,
 * avoiding circular dependencies with Flyway and Hibernate initialization.</p>
 *
 * <p>Can be disabled via: datasource-proxy.enabled=false</p>
 */
@Configuration
@Slf4j
@ConditionalOnProperty(name = "datasource-proxy.enabled", havingValue = "true", matchIfMissing = true)
public class DataSourceProxyConfig {

  private final QueryMetricsCollector queryMetricsCollector;

  public DataSourceProxyConfig(QueryMetricsCollector queryMetricsCollector) {
    this.queryMetricsCollector = queryMetricsCollector;
  }

  /**
   * Post-processor that wraps DataSource beans with the proxy after creation.
   * This approach avoids circular dependencies by applying the wrapper after
   * Flyway and Hibernate have completed their initialization.
   *
   * @return the DataSourceProxyPostProcessor bean
   */
  @Bean
  public DataSourceProxyPostProcessor dataSourceProxyPostProcessor() {
    return new DataSourceProxyPostProcessor(queryMetricsCollector);
  }

  /**
   * BeanPostProcessor that wraps DataSource with query execution listener.
   */
  @Slf4j
  public static class DataSourceProxyPostProcessor implements BeanPostProcessor {

    private final QueryMetricsCollector queryMetricsCollector;
    private boolean isDataSourceWrapped = false;

    /**
     * Constructor for DataSourceProxyPostProcessor.
     *
     * @param queryMetricsCollector the metrics collector listener
     */
    public DataSourceProxyPostProcessor(QueryMetricsCollector queryMetricsCollector) {
      this.queryMetricsCollector = queryMetricsCollector;
    }

    @Override
    public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName) {
      return bean;
    }

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) {
      // Wrap only the first DataSource bean we encounter (usually HikariDataSource)
      // to avoid wrapping it multiple times
      if (bean instanceof DataSource && !isDataSourceWrapped && !isProxy(beanName)) {
        isDataSourceWrapped = true;
        log.info("Wrapping DataSource '{}' with proxy for SQL metrics collection", beanName);

        return ProxyDataSourceBuilder
            .create((DataSource) bean)
            .name("LAA-Data-Access-API-DataSource-Proxy-" + beanName)
            .listener(queryMetricsCollector)
            .build();
      }
      return bean;
    }

    /**
     * Check if this bean is already a proxied datasource.
     *
     * @param beanName the bean name to check
     * @return true if the bean name indicates it's already proxied
     */
    private boolean isProxy(String beanName) {
      return beanName != null && beanName.contains("Proxy");
    }
  }
}
