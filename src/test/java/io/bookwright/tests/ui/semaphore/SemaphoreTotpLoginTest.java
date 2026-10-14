package io.bookwright.tests.ui.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.OwnerDanil;
import io.bookwright.annotations.SensitiveUi;
import io.bookwright.annotations.Ui;
import io.bookwright.api.model.semaphore.TotpPasscodeRequest;
import io.bookwright.api.model.semaphore.TotpRecoveryRequest;
import io.bookwright.api.model.semaphore.UserTotp;
import io.bookwright.fixtures.semaphore.SemaphoreTotpFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
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
@Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_TOTP_UI_USER_EXISTS})
class SemaphoreTotpLoginTest {

  @Test
  @DisplayName("Browser enrollment renders its QR code and recovery code")
  void browserEnrollmentShowsRecoveryMaterial(
      ApiSteps api, UiSteps ui, TestStore store, SemaphoreTotpFixtures fixtures) {
    var user = store.semaphoreTotpUser();
    var enrollment = ui.semaphore().totp().loginAndEnable(fixtures.uiAccount(), user.id());

    assertThat(api.semaphore().users().getUser(user.id()).totp().id()).isEqualTo(enrollment.id());
    ui.semaphore().totp().logout();
  }

  @Test
  @DisplayName("Invalid browser TOTP passcode leaves the challenge visible")
  void invalidPasscodeKeepsBrowserChallenge(
      ApiSteps api, UiSteps ui, TestStore store, SemaphoreTotpFixtures fixtures) {
    var enrollment = enroll(api, store, fixtures);

    ui.semaphore().totp().requireChallenge(fixtures.uiAccount());
    ui.semaphore()
        .totp()
        .reject(
            new TotpPasscodeRequest(TotpCodes.differentFrom(TotpCodes.current(enrollment.url()))));
  }

  @Test
  @DisplayName("Valid browser TOTP passcode opens the authenticated user page")
  void validPasscodeCompletesBrowserChallenge(
      ApiSteps api, UiSteps ui, TestStore store, SemaphoreTotpFixtures fixtures) {
    var enrollment = enroll(api, store, fixtures);

    ui.semaphore().totp().requireChallenge(fixtures.uiAccount());
    ui.semaphore().totp().verify(new TotpPasscodeRequest(TotpCodes.current(enrollment.url())));
    ui.semaphore().totp().logout();
  }

  @Test
  @DisplayName("Browser recovery authenticates the user and removes the TOTP binding")
  void browserRecoveryDisablesTotp(
      ApiSteps api, UiSteps ui, TestStore store, SemaphoreTotpFixtures fixtures) {
    var enrollment = enroll(api, store, fixtures);

    ui.semaphore().totp().requireChallenge(fixtures.uiAccount());
    ui.semaphore().totp().recover(new TotpRecoveryRequest(enrollment.recoveryCode()));
    assertThat(api.semaphore().users().getUser(store.semaphoreTotpUser().id()).totp())
        .as("TOTP binding after browser recovery")
        .isNull();
  }

  private UserTotp enroll(ApiSteps api, TestStore store, SemaphoreTotpFixtures fixtures) {
    var session = api.semaphore().auth().loginAs(fixtures.uiAccount().loginRequest());
    var enrollment = api.semaphore().users().enableTotp(session, store.semaphoreTotpUser().id());
    api.semaphore().auth().logout(session);
    return enrollment;
  }
}
