package uk.gov.justice.laa.dstew.access.config;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Flagd client config. */
@ExcludeFromGeneratedCodeCoverage
@RequiredArgsConstructor
@Configuration
@ConditionalOnProperty(prefix = "flagd", name = "enabled", havingValue = "true")
public class FlagdClientConfig {

  private final FlagdProperties flagdProperties;

  /** Register the Flagd as the OpenFeature provider. */
  @PostConstruct
  void registerProvider() {
    FlagdOptions options =
        FlagdOptions.builder()
            .host(flagdProperties.host())
            .port(flagdProperties.port())
            .deadline(5000)
            .build();
    OpenFeatureAPI.getInstance().setProvider(new FlagdProvider(options));
  }
}
