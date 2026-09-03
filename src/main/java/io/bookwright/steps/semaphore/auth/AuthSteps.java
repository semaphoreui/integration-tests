package io.bookwright.steps.semaphore.auth;

import com.google.inject.Inject;
import io.bookwright.api.RetrofitFactory;
import io.bookwright.api.model.semaphore.LoginRequest;
import io.bookwright.api.model.semaphore.SemaphoreTestUser;
import io.bookwright.api.model.semaphore.TotpPasscodeRequest;
import io.bookwright.api.model.semaphore.TotpRecoveryRequest;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.api.semaphore.auth.SemaphoreAuthApi;
import io.bookwright.config.MainConfig;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.io.IOException;
import retrofit2.Call;
import retrofit2.Response;

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
    try (var ignored = response.errorBody()) {
      if (response.code() < 400 || response.code() >= 500) {
        throw new IllegalStateException(
            "Expected invalid login to return 4xx but received " + response.code());
      }
    }
  }

  @Step("Verify invalid credentials do not disclose whether a Semaphore account exists")
  public void verifyInvalidCredentialsIndistinguishable(
      LoginRequest existingUser, LoginRequest unknownUser) {
    String existingUserBody = requireUnauthorizedWithoutSession(existingUser);
    String unknownUserBody = requireUnauthorizedWithoutSession(unknownUser);
    if (!existingUserBody.equals(unknownUserBody)) {
      throw new IllegalStateException(
          "Invalid login responses disclose whether the Semaphore account exists");
    }
  }

  @Step("Verify an empty Semaphore password is rejected without a session")
  public void verifyEmptyPasswordRejected(LoginRequest request) {
    Response<Void> response = Calls.response(api.login(request));
    try (var ignored = response.errorBody()) {
      if ((response.code() != 400 && response.code() != 401) || issuesSessionCookie(response)) {
        throw new IllegalStateException(
            "Empty password expected HTTP 400/401 without a session cookie but received HTTP %d"
                .formatted(response.code()));
      }
    }
  }

  @Step("Verify {attempts} repeated failed Semaphore logins remain unthrottled")
  public void verifyFailuresRemainUnthrottled(LoginRequest request, int attempts) {
    if (attempts < 1) {
      throw new IllegalArgumentException("Repeated login attempt count must be positive");
    }
    for (int attempt = 1; attempt <= attempts; attempt++) {
      requireUnauthorizedWithoutSession(request);
    }
  }

  @Step("Login to Semaphore API")
  public void login() {
    Calls.expectStatus(
        api.login(new LoginRequest(config.apiUsername(), config.apiPassword())), 204);
  }

  @Step("Login as isolated Semaphore user")
  public SemaphoreSessionApis loginAs(SemaphoreTestUser account) {
    return loginAs(new LoginRequest(account.user().username(), account.password()));
  }

  @Step("Login with isolated Semaphore credentials")
  public SemaphoreSessionApis loginAs(LoginRequest request) {
    var retrofit = RetrofitFactory.create(config.apiBaseUrl());
    var isolatedAuth = retrofit.create(SemaphoreAuthApi.class);
    Calls.expectStatus(isolatedAuth.login(request), 204);
    return SemaphoreSessionApis.create(retrofit);
  }

  @Step("Verify isolated Semaphore session requires TOTP")
  public void requireTotpChallenge(SemaphoreSessionApis session) {
    expectAuthError(session.users().getCurrentUser(), 401, "TOTP_REQUIRED", "TOTP challenge");
  }

  @Step("Verify invalid TOTP passcode is rejected")
  public void invalidTotpIsRejected(SemaphoreSessionApis session, TotpPasscodeRequest request) {
    expectAuthError(
        session.auth().verifyTotp(request), 401, "INVALID_PASSCODE", "invalid TOTP passcode");
  }

  @Step("Complete isolated Semaphore TOTP challenge")
  public void verifyTotp(SemaphoreSessionApis session, TotpPasscodeRequest request) {
    Calls.expectStatus(session.auth().verifyTotp(request), 200);
  }

  @Step("Verify consumed TOTP recovery code is rejected")
  public void invalidRecoveryCodeIsRejected(
      SemaphoreSessionApis session, TotpRecoveryRequest request) {
    expectAuthError(
        session.auth().recoverTotp(request),
        401,
        "INVALID_RECOVERY_CODE",
        "invalid TOTP recovery code");
  }

  @Step("Recover isolated Semaphore TOTP session")
  public void recoverTotp(SemaphoreSessionApis session, TotpRecoveryRequest request) {
    Calls.expectStatus(session.auth().recoverTotp(request), 204);
  }

  @Step("Log out isolated Semaphore session")
  public void logout(SemaphoreSessionApis session) {
    Calls.expectStatus(session.auth().logout(), 204);
    Calls.expectStatus(session.users().getCurrentUser(), 401);
  }

  private void expectAuthError(
      Call<?> call, int expectedStatus, String expectedError, String operation) {
    var response = Calls.response(call);
    String body;
    try {
      body = response.errorBody() == null ? "" : response.errorBody().string();
    } catch (IOException error) {
      throw new IllegalStateException("Could not read " + operation + " response", error);
    }
    if (response.code() != expectedStatus || !body.contains(expectedError)) {
      throw new IllegalStateException(
          "%s expected HTTP %d with %s but received HTTP %d: %s"
              .formatted(operation, expectedStatus, expectedError, response.code(), body));
    }
  }

  private String requireUnauthorizedWithoutSession(LoginRequest request) {
    Response<Void> response = Calls.response(api.login(request));
    if (response.code() != 401
        || response.headers().get("Retry-After") != null
        || issuesSessionCookie(response)) {
      throw new IllegalStateException(
          "Invalid login expected an unthrottled HTTP 401 without a session cookie but received HTTP %d"
              .formatted(response.code()));
    }
    try (var body = response.errorBody()) {
      return body == null ? "" : body.string();
    } catch (IOException error) {
      throw new IllegalStateException("Could not read invalid login response", error);
    }
  }

  private boolean issuesSessionCookie(Response<?> response) {
    return response.headers().values("Set-Cookie").stream()
        .anyMatch(cookie -> cookie.startsWith("semaphore="));
  }
}
