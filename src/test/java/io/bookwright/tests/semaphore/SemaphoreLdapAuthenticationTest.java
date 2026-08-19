package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.annotations.Smoke;
import io.bookwright.api.model.semaphore.User;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.fixtures.semaphore.SemaphoreLdapFixtures;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Api
@Smoke
@OwnerDanil
@Feature("Semaphore LDAP authentication")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "feature-ldap-tls")
class SemaphoreLdapAuthenticationTest {

  @Test
  @DisplayName("LDAP user can sign in and is provisioned as an external user")
  void ldapUserCanSignIn(ApiSteps api, SemaphoreLdapFixtures fixtures) {
    assertThat(
            api.semaphore()
                .users()
                .currentUser(api.semaphore().auth().loginAs(fixtures.successfulLogin().request())))
        .returns(fixtures.successfulLogin().username(), User::username)
        .returns(fixtures.successfulLogin().email(), User::email)
        .returns(true, User::external)
        .returns(false, User::admin);

    api.semaphore().auth().login();
    assertThat(api.semaphore().users().findByUsername(fixtures.successfulLogin().username()))
        .returns(fixtures.successfulLogin().email(), User::email)
        .returns(true, User::external);
  }

  @Test
  @DisplayName("Repeated LDAP login reuses the provisioned user after logout")
  void repeatedLdapLoginReusesUser(ApiSteps api, SemaphoreLdapFixtures fixtures) {
    SemaphoreSessionApis firstSession =
        api.semaphore().auth().loginAs(fixtures.successfulLogin().request());
    long provisionedUserId = api.semaphore().users().currentUser(firstSession).id();

    api.semaphore().auth().logout(firstSession);

    assertThat(
            api.semaphore()
                .users()
                .currentUser(api.semaphore().auth().loginAs(fixtures.successfulLogin().request())))
        .returns(provisionedUserId, User::id);
  }

  @Test
  @DisplayName("Wrong LDAP password is rejected without provisioning a user")
  void wrongLdapPasswordIsRejected(ApiSteps api, SemaphoreLdapFixtures fixtures) {
    api.semaphore().auth().invalidLoginIsRejected(fixtures.invalidPassword().request());
    api.semaphore().auth().login();

    assertThat(api.semaphore().users().getUsers())
        .noneMatch(user -> fixtures.invalidPassword().username().equals(user.username()));
  }

  @Test
  @DisplayName("LDAP login cannot take over a local account with the same email")
  void ldapLoginCannotTakeOverLocalAccount(ApiSteps api, SemaphoreLdapFixtures fixtures) {
    api.semaphore().auth().invalidLoginIsRejected(fixtures.localEmailConflict().request());
    api.semaphore().auth().login();

    assertThat(api.semaphore().users().getUsers())
        .filteredOn(user -> fixtures.localEmailConflict().email().equals(user.email()))
        .singleElement()
        .returns(false, User::external)
        .returns(true, User::admin);
  }
}
