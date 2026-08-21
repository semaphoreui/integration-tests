package io.bookwright.steps.ui.semaphore.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.TotpPasscodeRequest;
import io.bookwright.api.model.semaphore.TotpRecoveryRequest;
import io.bookwright.api.model.semaphore.UserTotp;
import io.bookwright.fixtures.semaphore.SemaphoreTotpFixtures.Account;
import io.bookwright.ui.SemaphoreTotpPage;
import io.qameta.allure.Step;

public class TotpLoginSteps {

  private final SemaphoreTotpPage page;

  @Inject
  public TotpLoginSteps(SemaphoreTotpPage page) {
    this.page = page;
  }

  @Step("Log in and enable TOTP in Semaphore Security settings")
  public UserTotp loginAndEnable(Account account, long userId) {
    page.loginAndWaitForUsers(account);
    return page.enableTotp(account, userId);
  }

  @Step("Log out from the TOTP enrollment session")
  public void logout() {
    page.closeAccountDialogAndLogout();
  }

  @Step("Log in with password and require the Semaphore TOTP challenge")
  public void requireChallenge(Account account) {
    page.loginAndWaitForTotpChallenge(account);
    assertThat(page.totpChallengeVisible()).as("TOTP challenge").isTrue();
  }

  @Step("Reject an invalid TOTP passcode in the Semaphore UI")
  public void reject(TotpPasscodeRequest request) {
    assertThat(page.submitPasscode(request)).as("invalid TOTP verification status").isEqualTo(401);
    assertThat(page.totpChallengeVisible()).as("challenge after invalid TOTP").isTrue();
  }

  @Step("Complete the Semaphore UI TOTP challenge")
  public void verify(TotpPasscodeRequest request) {
    assertThat(page.submitPasscode(request)).as("valid TOTP verification status").isEqualTo(200);
    page.waitForUsers();
  }

  @Step("Recover the Semaphore account through the TOTP recovery form")
  public void recover(TotpRecoveryRequest request) {
    assertThat(page.submitRecoveryCode(request)).as("TOTP recovery status").isEqualTo(204);
    page.waitForUsers();
  }
}
