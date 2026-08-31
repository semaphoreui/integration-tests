package io.bookwright.steps.semaphore.tokens;

import com.google.inject.Inject;
import io.bookwright.api.RetrofitFactory;
import io.bookwright.api.model.semaphore.ApiToken;
import io.bookwright.api.model.semaphore.ApiTokenRequest;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.api.semaphore.tokens.SemaphoreTokensApi;
import io.bookwright.config.MainConfig;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.util.List;

public class TokenSteps {

  private final SemaphoreTokensApi api;
  private final MainConfig config;
  private final TeardownStorage teardown;

  @Inject
  public TokenSteps(SemaphoreTokensApi api, MainConfig config, TeardownStorage teardown) {
    this.api = api;
    this.config = config;
    this.teardown = teardown;
  }

  @Step("Create Semaphore API token {request.name}")
  public ApiToken create(ApiTokenRequest request) {
    ApiToken token = Calls.body(api.create(request), 201, "created API token");
    teardown.push("Delete Semaphore API token " + token.prefix(), () -> deleteIfPresent(token));
    return token;
  }

  @Step("Get Semaphore API tokens")
  public List<ApiToken> getTokens() {
    return Calls.body(api.getTokens(), 200, "API tokens");
  }

  @Step("Find Semaphore API token {name}")
  public ApiToken requireByName(String name) {
    List<ApiToken> tokens = getTokens();
    return tokens.stream()
        .filter(token -> name.equals(token.name()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Required Semaphore API token '%s' was not found. Available tokens: %s"
                        .formatted(name, tokens.stream().map(ApiToken::name).toList())));
  }

  @Step("Authenticate with Semaphore API token {token.name}")
  public SemaphoreSessionApis authenticate(ApiToken token) {
    return SemaphoreSessionApis.create(
        RetrofitFactory.createWithBearerToken(config.apiBaseUrl(), token.id()));
  }

  @Step("Delete Semaphore API token {token.name}")
  public void delete(ApiToken token) {
    Calls.expectStatus(api.delete(token.prefix()), 204);
  }

  @Step("Verify rejected Semaphore API token request")
  public void verifyRejected(SemaphoreSessionApis session) {
    Calls.expectStatus(session.users().getCurrentUser(), 401);
  }

  @Step("Verify expired Semaphore API token request is rejected")
  public void verifyExpiredRequestRejected(ApiTokenRequest request) {
    Calls.expectStatus(api.create(request), 400);
  }

  private void deleteIfPresent(ApiToken token) {
    var response = Calls.response(api.delete(token.prefix()));
    if (response.code() != 204 && response.code() != 404) {
      throw new IllegalStateException(
          "API token cleanup expected HTTP 204 or 404 but received " + response.code());
    }
  }
}
