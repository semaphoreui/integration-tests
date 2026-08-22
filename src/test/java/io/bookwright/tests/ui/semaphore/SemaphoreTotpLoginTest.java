package io.bookwright.tests.ui.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.OwnerDanil;
import io.bookwright.annotations.SensitiveUi;
import io.bookwright.annotations.Ui;
import io.bookwright.api.model.semaphore.TotpPasscodeRequest;
import io.bookwright.api.model.semaphore.TotpRecoveryRequest;
import io.bookwright.fixtures.semaphore.SemaphoreTotpFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.bookwright.steps.UiSteps;
import io.bookwright.util.TotpCodes;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Ui
@SensitiveUi
@OwnerDanil
@Feature("Semaphore TOTP authentication")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "feature-totp-local")
class SemaphoreTotpLoginTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("User enrolls TOTP and completes challenge and recovery in the browser")
  void totpBrowserLifecycle(ApiSteps api, UiSteps ui, SemaphoreTotpFixtures fixtures) {
    var account = fixtures.uiAccount();
    var user = api.semaphore().users().getOrCreate(account.userRequest());
    api.semaphore().users().ensureTotpDisabled(user);

    var enrollment = ui.semaphore().totp().loginAndEnable(account, user.id());
    ui.semaphore().totp().logout();

    ui.semaphore().totp().requireChallenge(account);
    var passcode = TotpCodes.current(enrollment.url());
    ui.semaphore().totp().reject(new TotpPasscodeRequest(TotpCodes.differentFrom(passcode)));
    ui.semaphore().totp().verify(new TotpPasscodeRequest(passcode));
    ui.semaphore().totp().logout();

    ui.semaphore().totp().requireChallenge(account);
    ui.semaphore().totp().recover(new TotpRecoveryRequest(enrollment.recoveryCode()));
    assertThat(api.semaphore().users().findByUsername(account.username()).totp())
        .as("TOTP binding after browser recovery")
        .isNull();
  }
}
