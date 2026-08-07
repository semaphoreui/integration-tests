package io.bookwright.steps.semaphore.auth;

import com.google.inject.Inject;
import io.bookwright.api.RetrofitFactory;
import io.bookwright.api.model.semaphore.LoginRequest;
import io.bookwright.api.model.semaphore.SemaphoreTestUser;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.api.semaphore.accesskeys.SemaphoreAccessKeysApi;
import io.bookwright.api.semaphore.auth.SemaphoreAuthApi;
import io.bookwright.api.semaphore.projects.SemaphoreProjectsApi;
import io.bookwright.config.MainConfig;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;

public class AuthSteps {

  private final SemaphoreAuthApi api;
  private final MainConfig config;

  @Inject
  public AuthSteps(SemaphoreAuthApi api, MainConfig config) {
    this.api = api;
    this.config = config;
  }

  @Step("Check that invalid Semaphore credentials are rejected")
  public void invalidLoginIsRejected(LoginRequest request) {
    var response = Calls.response(api.login(request));
    if (response.code() < 400 || response.code() >= 500) {
      throw new IllegalStateException(
          "Expected invalid login to return 4xx but received " + response.code());
    }
  }

  @Step("Login to Semaphore API")
  public void login() {
    Calls.expectStatus(
        api.login(new LoginRequest(config.apiUsername(), config.apiPassword())), 204);
  }

  @Step("Login as isolated Semaphore user")
  public SemaphoreSessionApis loginAs(SemaphoreTestUser account) {
    var retrofit = RetrofitFactory.create(config.apiBaseUrl());
    var isolatedAuth = retrofit.create(SemaphoreAuthApi.class);
    Calls.expectStatus(
        isolatedAuth.login(new LoginRequest(account.user().username(), account.password())), 204);
    return new SemaphoreSessionApis(
        retrofit.create(SemaphoreProjectsApi.class), retrofit.create(SemaphoreAccessKeysApi.class));
  }
}
