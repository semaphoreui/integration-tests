package io.bookwright.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import io.bookwright.api.model.semaphore.TotpPasscodeRequest;
import io.bookwright.api.model.semaphore.TotpRecoveryRequest;
import io.bookwright.api.model.semaphore.UserTotp;
import io.bookwright.config.MainConfig;
import io.bookwright.fixtures.semaphore.SemaphoreTotpFixtures.Account;

public class SemaphoreTotpPage {

  private static final ObjectMapper JSON =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private static final String USERS_PATH = "/users";

  private final Page page;
  private final MainConfig config;

  @Inject
  public SemaphoreTotpPage(Page page, MainConfig config) {
    this.page = page;
    this.config = config;
  }

  public void loginAndWaitForUsers(Account account) {
    submitPassword(account, USERS_PATH);
    waitForUsers();
  }

  public void loginAndWaitForTotpChallenge(Account account) {
    submitPassword(account, USERS_PATH);
    page.getByRole(
            AriaRole.HEADING,
            new Page.GetByRoleOptions().setName("Two-step verification").setExact(true))
        .waitFor();
  }

  public UserTotp enableTotp(Account account, long userId) {
    page.getByText(account.username(), new Page.GetByTextOptions().setExact(true))
        .locator("xpath=ancestor::tr")
        .locator("button")
        .nth(1)
        .click();
    page.getByText("Security", new Page.GetByTextOptions().setExact(true)).click();

    Response response =
        page.waitForResponse(
            candidate ->
                "POST".equals(candidate.request().method())
                    && candidate.url().endsWith("/api/users/%d/2fas/totp".formatted(userId)),
            () ->
                page.getByText(
                        "Time-based one-time password", new Page.GetByTextOptions().setExact(true))
                    .click());
    if (response.status() != 200) {
      throw new IllegalStateException(
          "Semaphore TOTP enrollment returned HTTP " + response.status());
    }

    UserTotp enrollment = parseEnrollment(response.text());
    Locator qr = page.getByAltText("QR code");
    qr.waitFor();
    page.waitForCondition(
        () -> ((Number) qr.evaluate("image => image.naturalWidth")).intValue() > 0);
    if (!qr.getAttribute("src").endsWith("/%d/qr".formatted(enrollment.id()))) {
      throw new IllegalStateException("Semaphore rendered a QR code for a different TOTP binding");
    }

    String renderedRecoveryCode =
        page.getByRole(AriaRole.DIALOG).locator("code").textContent().trim();
    if (!enrollment.recoveryCode().equals(renderedRecoveryCode)) {
      throw new IllegalStateException("Semaphore rendered an unexpected TOTP recovery code");
    }
    return enrollment;
  }

  public void closeAccountDialogAndLogout() {
    page.keyboard().press("Escape");
    APIResponse response = page.context().request().post(config.uiBaseUrl() + "/api/auth/logout");
    try {
      if (response.status() != 204) {
        throw new IllegalStateException("Semaphore logout returned HTTP " + response.status());
      }
    } finally {
      response.dispose();
    }
    page.navigate(config.uiBaseUrl() + "/auth/login");
  }

  public int submitPasscode(TotpPasscodeRequest request) {
    return submitVerification(() -> fillOtp(request.passcode()), "/api/auth/verify");
  }

  public void waitForUsers() {
    page.waitForURL(config.uiBaseUrl() + USERS_PATH);
  }

  public boolean totpChallengeVisible() {
    return page.getByText("Two-step verification", new Page.GetByTextOptions().setExact(true))
        .isVisible();
  }

  public int submitRecoveryCode(TotpRecoveryRequest request) {
    page.getByText("Use recovery code", new Page.GetByTextOptions().setExact(true)).click();
    return submitVerification(
        () -> {
          page.getByLabel("Recovery code").fill(request.recoveryCode());
          page.getByRole(
                  AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true))
              .click();
        },
        "/api/auth/recovery");
  }

  private void submitPassword(Account account, String returnPath) {
    page.navigate(config.uiBaseUrl() + "/auth/login?return=" + returnPath);
    page.getByTestId("auth-username").fill(account.username());
    page.getByTestId("auth-password").fill(account.password());
    page.getByTestId("auth-signin").click();
  }

  private int submitVerification(Runnable submission, String endpoint) {
    return page.waitForResponse(
            response ->
                "POST".equals(response.request().method()) && response.url().endsWith(endpoint),
            submission)
        .status();
  }

  private void fillOtp(String passcode) {
    Locator digits = page.locator(".v-otp-input input");
    if (digits.count() != passcode.length()) {
      throw new IllegalStateException("Semaphore did not render six TOTP input fields");
    }
    for (int index = 0; index < passcode.length(); index++) {
      digits.nth(index).fill(String.valueOf(passcode.charAt(index)));
    }
  }

  private UserTotp parseEnrollment(String responseBody) {
    try {
      return JSON.readValue(responseBody, UserTotp.class);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException(
          "Semaphore returned an invalid TOTP enrollment payload", error);
    }
  }
}
