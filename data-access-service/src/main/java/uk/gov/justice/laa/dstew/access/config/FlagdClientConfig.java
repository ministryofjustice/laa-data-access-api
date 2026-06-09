package uk.gov.justice.laa.dstew.access.config;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RequiredArgsConstructor
@Configuration
@ConditionalOnProperty(prefix = "flagd", name = "enabled", havingValue = "true")
public class FlagdClientConfig {

  private final FlagdProperties flagdProperties;

  @PostConstruct
  void registerProvider() {
    FlagdOptions options =
        FlagdOptions.builder()
            .host(flagdProperties.host())
            .port(flagdProperties.port())
            .build();
    try {
      OpenFeatureAPI.getInstance().setProviderAndWait(new FlagdProvider(options));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize OpenFeature flagd provider", e);
    }
  }
}




