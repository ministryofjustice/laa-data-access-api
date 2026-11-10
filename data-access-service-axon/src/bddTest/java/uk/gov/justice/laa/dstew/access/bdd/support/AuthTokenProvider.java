package uk.gov.justice.laa.dstew.access.bdd.support;

import uk.gov.justice.laa.dstew.access.bdd.config.BddConfiguration;

/** Provides the scenario's configured bearer token without exposing it to step definitions. */
public final class AuthTokenProvider {
  private final String token;

  public AuthTokenProvider(BddConfiguration configuration) {
    token = configuration.token();
  }

  public String bearerToken() {
    return token;
  }
}
