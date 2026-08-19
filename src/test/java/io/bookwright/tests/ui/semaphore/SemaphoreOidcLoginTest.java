package io.bookwright.tests.ui.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.OwnerDanil;
import io.bookwright.annotations.Smoke;
import io.bookwright.annotations.Ui;
import io.bookwright.api.model.semaphore.User;
import io.bookwright.fixtures.semaphore.SemaphoreOidcFixtures;
import io.bookwright.steps.ApiSteps;
import io.bookwright.steps.UiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Ui
@Smoke
@OwnerDanil
@Feature("Semaphore OIDC authentication")
class SemaphoreOidcLoginTest {

  @Test
  @DisplayName("OIDC user can sign in and is provisioned as an external user")
  void oidcUserCanSignIn(UiSteps ui, ApiSteps api, SemaphoreOidcFixtures fixtures) {
    ui.semaphore().oidc().login(fixtures.successfulLogin());
    api.semaphore().auth().login();

    assertThat(
            api.semaphore().users().findByUsername(fixtures.successfulLogin().account().username()))
        .returns(fixtures.successfulLogin().account().username(), User::username)
        .returns(fixtures.successfulLogin().account().email(), User::email)
        .returns(true, User::external)
        .returns(false, User::admin);
  }

  @Test
  @DisplayName("Repeated OIDC login reuses the provisioned user")
  void repeatedOidcLoginReusesUser(UiSteps ui, ApiSteps api, SemaphoreOidcFixtures fixtures) {
    ui.semaphore().oidc().login(fixtures.successfulLogin());
    api.semaphore().auth().login();
    long provisionedUserId =
        api.semaphore()
            .users()
            .findByUsername(fixtures.successfulLogin().account().username())
            .id();

    ui.semaphore().oidc().logout();
    ui.semaphore().oidc().login(fixtures.successfulLogin());

    assertThat(api.semaphore().users().getUsers())
        .filteredOn(user -> fixtures.successfulLogin().account().email().equals(user.email()))
        .singleElement()
        .returns(provisionedUserId, User::id);
  }

  @Test
  @DisplayName("OIDC login cannot take over a local account with the same email")
  void oidcLoginCannotTakeOverLocalAccount(
      UiSteps ui, ApiSteps api, SemaphoreOidcFixtures fixtures) {
    ui.semaphore().oidc().loginAndExpectRejected(fixtures.localEmailConflict());
    api.semaphore().auth().login();

    assertThat(api.semaphore().users().getUsers())
        .filteredOn(user -> fixtures.localEmailConflict().account().email().equals(user.email()))
        .singleElement()
        .returns(false, User::external)
        .returns(true, User::admin);
  }

  @Test
  @DisplayName("OIDC provider outage does not create a session")
  void unavailableOidcProviderDoesNotCreateSession(UiSteps ui, SemaphoreOidcFixtures fixtures) {
    ui.semaphore().oidc().unavailableProviderIsRejected(fixtures.unavailableProvider());
  }
}
