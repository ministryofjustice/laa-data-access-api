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
    System.err.println("DEBUG: ScenarioHooks.startScenario() INVOKED");
  }

  @After
  public void stopScenario() {
    System.err.println("DEBUG: ScenarioHooks.stopScenario() INVOKED");
    apiClient.close();
  }
}
