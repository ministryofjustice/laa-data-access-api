package uk.gov.justice.laa.dstew.access.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** OAuth2 client properties for outbound SDS API calls. */
@Data
@ConfigurationProperties(prefix = "app.sds-api.oauth2")
public class SdsOauth2Properties {
  private String clientId;
  private String clientSecret;
  private String scope;
  private String tokenUri;
}
