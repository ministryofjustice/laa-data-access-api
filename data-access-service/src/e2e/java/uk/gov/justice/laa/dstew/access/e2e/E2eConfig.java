package uk.gov.justice.laa.dstew.access.e2e;

import org.aeonbits.owner.Config;

@Config.Sources({
    "system:properties",
    "system:env",
    "classpath:${env}.properties",
    "classpath:local.properties"
})
public interface E2eConfig extends Config {

  @Key("base.url")
  String baseUrl();

  @Key("base.path")
  String basePath();

  @Key("db.url")
  String dbUrl();

  @Key("db.username")
  String dbUsername();

  @Key("db.password")
  String dbPassword();
}
