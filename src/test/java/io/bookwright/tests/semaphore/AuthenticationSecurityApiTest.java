package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreLoginSecurityFixtures;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore password authentication security")
class AuthenticationSecurityApiTest {

  @Test
  @DisplayName("Invalid credentials do not disclose account existence or create a session")
  void invalidCredentialsDoNotDiscloseAccount(
      ApiSteps api, SemaphoreLoginSecurityFixtures fixtures) {
    api.semaphore()
        .auth()
        .verifyInvalidCredentialsIndistinguishable(
            fixtures.existingUserWrongPassword(), fixtures.unknownUser());
    api.semaphore().auth().verifyEmptyPasswordRejected(fixtures.emptyPassword());
  }

  @Test
  @DisplayName("Known gap: repeated failed password logins remain unthrottled")
  void repeatedFailedLoginsRemainUnthrottled(
      ApiSteps api, SemaphoreLoginSecurityFixtures fixtures) {
    api.semaphore()
        .auth()
        .verifyFailuresRemainUnthrottled(
            fixtures.existingUserWrongPassword(), fixtures.repeatedFailureCount());

    var session = api.semaphore().auth().loginAs(fixtures.correct());
    assertThat(api.semaphore().users().currentUser(session).username())
        .isEqualTo(fixtures.correct().auth());
  }
}
