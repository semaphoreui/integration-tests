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
  void oidcUserCanSignIn(UiSteps ui, ApiSteps api, SemaphoreOidcFixtures fixture) {
    ui.semaphore().oidc().login(fixture);
    api.semaphore().auth().login();

    assertThat(api.semaphore().users().findByUsername(fixture.username()))
        .returns(fixture.username(), User::username)
        .returns(fixture.email(), User::email)
        .returns(true, User::external)
        .returns(false, User::admin);
  }
}
