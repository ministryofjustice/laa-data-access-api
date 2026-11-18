package uk.gov.justice.laa.dstew.access.bdd.steps;

import io.cucumber.java.en.Given;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiClient;
import uk.gov.justice.laa.dstew.access.bdd.support.AuthTokenProvider;
import uk.gov.justice.laa.dstew.access.bdd.support.ScenarioContext;

public final class AuthenticationSteps {
  private final ApiClient apiClient;
  private final AuthTokenProvider tokenProvider;
  private final ScenarioContext context;

  public AuthenticationSteps(
      ApiClient apiClient, AuthTokenProvider tokenProvider, ScenarioContext context) {
    this.apiClient = apiClient;
    this.tokenProvider = tokenProvider;
    this.context = context;
  }

  @Given("I am logged in as {string}")
  public void loggedInAs(String tokenName) {
    AuthTokenProvider.AuthToken token = tokenProvider.fetch(tokenName);
    apiClient.useBearerToken(token.value());
    context.officeCode(token.officeCode());
  }
}
