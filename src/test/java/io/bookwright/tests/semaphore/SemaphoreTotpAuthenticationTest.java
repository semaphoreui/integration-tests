package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.api.model.semaphore.TotpPasscodeRequest;
import io.bookwright.api.model.semaphore.TotpRecoveryRequest;
import io.bookwright.api.model.semaphore.UserTotp;
import io.bookwright.fixtures.semaphore.SemaphoreTotpFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
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
@Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_TOTP_API_USER_EXISTS})
class SemaphoreTotpAuthenticationTest {

  @Test
  @DisplayName("TOTP enrollment returns issuer and one-time recovery material")
  void enrollmentReturnsRecoveryMaterialOnlyOnce(
      ApiSteps api, TestStore store, SemaphoreTotpFixtures fixtures) {
    var session = api.semaphore().auth().loginAs(fixtures.apiAccount().loginRequest());
    var enrollment = api.semaphore().users().enableTotp(session, store.semaphoreTotpUser().id());

    requireEnrollmentMaterial(enrollment.url(), enrollment.recoveryCode());
    assertThat(api.semaphore().users().currentUser(session).totp().recoveryCode())
        .as("recovery code is returned only by enrollment")
        .isNull();
    api.semaphore().auth().logout(session);
  }

  @Test
  @DisplayName("Invalid TOTP passcode keeps the session behind the challenge")
  void invalidPasscodeDoesNotAuthenticate(
      ApiSteps api, TestStore store, SemaphoreTotpFixtures fixtures) {
    var enrollment = enroll(api, store, fixtures);
    var session = api.semaphore().auth().loginAs(fixtures.apiAccount().loginRequest());

    api.semaphore().auth().requireTotpChallenge(session);
    api.semaphore()
        .auth()
        .invalidTotpIsRejected(
            session,
            new TotpPasscodeRequest(TotpCodes.differentFrom(TotpCodes.current(enrollment.url()))));
    api.semaphore().auth().requireTotpChallenge(session);
  }

  @Test
  @DisplayName("Valid TOTP passcode authenticates the password session")
  void validPasscodeAuthenticates(ApiSteps api, TestStore store, SemaphoreTotpFixtures fixtures) {
    var enrollment = enroll(api, store, fixtures);
    var session = api.semaphore().auth().loginAs(fixtures.apiAccount().loginRequest());

    api.semaphore().auth().requireTotpChallenge(session);
    api.semaphore()
        .auth()
        .verifyTotp(session, new TotpPasscodeRequest(TotpCodes.current(enrollment.url())));
    assertThat(api.semaphore().users().currentUser(session).id())
        .isEqualTo(store.semaphoreTotpUser().id());
    api.semaphore().auth().logout(session);
  }

  @Test
  @DisplayName("Recovery disables TOTP and a consumed code cannot recover a new enrollment")
  void recoveryCodeCannotBeReusedAfterReenrollment(
      ApiSteps api, TestStore store, SemaphoreTotpFixtures fixtures) {
    var firstEnrollment = enroll(api, store, fixtures);
    var recoverySession = api.semaphore().auth().loginAs(fixtures.apiAccount().loginRequest());
    api.semaphore().auth().requireTotpChallenge(recoverySession);
    api.semaphore()
        .auth()
        .recoverTotp(recoverySession, new TotpRecoveryRequest(firstEnrollment.recoveryCode()));
    assertThat(api.semaphore().users().currentUser(recoverySession).totp()).isNull();

    var secondEnrollment =
        api.semaphore().users().enableTotp(recoverySession, store.semaphoreTotpUser().id());
    requireRotatedRecoveryCode(firstEnrollment.recoveryCode(), secondEnrollment.recoveryCode());
    api.semaphore().auth().logout(recoverySession);

    var session = api.semaphore().auth().loginAs(fixtures.apiAccount().loginRequest());
    api.semaphore().auth().requireTotpChallenge(session);
    api.semaphore()
        .auth()
        .invalidRecoveryCodeIsRejected(
            session, new TotpRecoveryRequest(firstEnrollment.recoveryCode()));
    api.semaphore()
        .auth()
        .recoverTotp(session, new TotpRecoveryRequest(secondEnrollment.recoveryCode()));
    assertThat(api.semaphore().users().currentUser(session).totp()).isNull();
    api.semaphore().auth().logout(session);
  }

  private UserTotp enroll(ApiSteps api, TestStore store, SemaphoreTotpFixtures fixtures) {
    var session = api.semaphore().auth().loginAs(fixtures.apiAccount().loginRequest());
    var enrollment = api.semaphore().users().enableTotp(session, store.semaphoreTotpUser().id());
    api.semaphore().auth().logout(session);
    return enrollment;
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
