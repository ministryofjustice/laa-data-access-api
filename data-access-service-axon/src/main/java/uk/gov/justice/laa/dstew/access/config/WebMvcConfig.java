package uk.gov.justice.laa.dstew.access.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameInterceptor;

/** Registers HTTP interceptors for API requests. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  private final ServiceNameInterceptor serviceNameInterceptor;

  public WebMvcConfig(ServiceNameInterceptor serviceNameInterceptor) {
    this.serviceNameInterceptor = serviceNameInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(serviceNameInterceptor).addPathPatterns("/api/**");
  }
}
