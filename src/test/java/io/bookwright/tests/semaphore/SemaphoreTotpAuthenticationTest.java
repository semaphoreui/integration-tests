package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.api.model.semaphore.TotpPasscodeRequest;
import io.bookwright.api.model.semaphore.TotpRecoveryRequest;
import io.bookwright.fixtures.semaphore.SemaphoreTotpFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.bookwright.util.TotpCodes;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Api
@OwnerDanil
@Feature("Semaphore TOTP authentication")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "feature-totp-local")
class SemaphoreTotpAuthenticationTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("TOTP enrollment protects login and recovery codes are single-use")
  void totpLoginAndRecovery(ApiSteps api, SemaphoreTotpFixtures fixtures) {
    var account = fixtures.apiAccount();
    var user = api.semaphore().users().getOrCreate(account.userRequest());
    api.semaphore().users().ensureTotpDisabled(user);
    var enrollmentSession = api.semaphore().auth().loginAs(account.loginRequest());
    var firstEnrollment = api.semaphore().users().enableTotp(enrollmentSession, user.id());

    requireEnrollmentMaterial(firstEnrollment.url(), firstEnrollment.recoveryCode());
    assertThat(api.semaphore().users().currentUser(enrollmentSession).totp().recoveryCode())
        .as("recovery code is returned only by enrollment")
        .isNull();
    api.semaphore().auth().logout(enrollmentSession);

    var totpSession = api.semaphore().auth().loginAs(account.loginRequest());
    api.semaphore().auth().requireTotpChallenge(totpSession);
    var currentPasscode = TotpCodes.current(firstEnrollment.url());
    api.semaphore()
        .auth()
        .invalidTotpIsRejected(
            totpSession, new TotpPasscodeRequest(TotpCodes.differentFrom(currentPasscode)));
    api.semaphore().auth().verifyTotp(totpSession, new TotpPasscodeRequest(currentPasscode));
    assertThat(api.semaphore().users().currentUser(totpSession).id()).isEqualTo(user.id());
    api.semaphore().auth().logout(totpSession);

    var recoverySession = api.semaphore().auth().loginAs(account.loginRequest());
    api.semaphore().auth().requireTotpChallenge(recoverySession);
    api.semaphore()
        .auth()
        .recoverTotp(recoverySession, new TotpRecoveryRequest(firstEnrollment.recoveryCode()));
    assertThat(api.semaphore().users().currentUser(recoverySession).totp()).isNull();

    var secondEnrollment = api.semaphore().users().enableTotp(recoverySession, user.id());
    requireRotatedRecoveryCode(firstEnrollment.recoveryCode(), secondEnrollment.recoveryCode());
    api.semaphore().auth().logout(recoverySession);

    var regeneratedRecoverySession = api.semaphore().auth().loginAs(account.loginRequest());
    api.semaphore().auth().requireTotpChallenge(regeneratedRecoverySession);
    api.semaphore()
        .auth()
        .invalidRecoveryCodeIsRejected(
            regeneratedRecoverySession, new TotpRecoveryRequest(firstEnrollment.recoveryCode()));
    api.semaphore()
        .auth()
        .recoverTotp(
            regeneratedRecoverySession, new TotpRecoveryRequest(secondEnrollment.recoveryCode()));
    assertThat(api.semaphore().users().currentUser(regeneratedRecoverySession).totp()).isNull();
    api.semaphore().auth().logout(regeneratedRecoverySession);
  }

  private void requireEnrollmentMaterial(String enrollmentUrl, String recoveryCode) {
    if (enrollmentUrl == null
        || !enrollmentUrl.startsWith("otpauth://totp/")
        || !enrollmentUrl.contains("issuer=Semaphore")) {
      throw new IllegalStateException("TOTP enrollment did not return the expected issuer URL");
    }
    if (recoveryCode == null || recoveryCode.isBlank()) {
      throw new IllegalStateException("TOTP enrollment did not return a recovery code");
    }
  }

  private void requireRotatedRecoveryCode(String previous, String current) {
    if (current == null || current.isBlank() || current.equals(previous)) {
      throw new IllegalStateException("TOTP re-enrollment did not rotate the recovery code");
    }
  }
}
