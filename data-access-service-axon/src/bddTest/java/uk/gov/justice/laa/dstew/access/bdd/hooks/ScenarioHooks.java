package uk.gov.justice.laa.dstew.access.bdd.hooks;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiClient;

public final class ScenarioHooks {
  private final ApiClient apiClient;

  public ScenarioHooks(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  @Before
  public void startScenario() {
    // Constructing the scenario-scoped client validates environment configuration and
    // authentication.
  }

  @After
  public void stopScenario() {
    apiClient.close();
  }
}
