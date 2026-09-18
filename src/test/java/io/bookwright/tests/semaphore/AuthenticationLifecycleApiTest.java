package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.api.model.semaphore.LoginMetadata;
import io.bookwright.fixtures.semaphore.SemaphoreAuthLifecycleFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore authentication lifecycle")
@Preconditions({
  Precondition.SEMAPHORE_ADMIN_SESSION,
  Precondition.SEMAPHORE_AUTH_LIFECYCLE_USERS_EXIST
})
class AuthenticationLifecycleApiTest {

  @Test
  @DisplayName("Login metadata is consistent and logout invalidates the session")
  void loginMetadataAndLogoutInvalidateSession(ApiSteps api, TestStore store) {
    assertThat(api.semaphore().auth().loginMetadata())
        .returns(true, LoginMetadata::loginWithPassword)
        .satisfies(
            metadata -> {
              assertThat(metadata.oidcProviders()).isNotNull();
              assertThat(metadata.ldapProviders()).isNotNull();
              assertThat(metadata.loginWithLdap()).isEqualTo(!metadata.ldapProviders().isEmpty());
            });

    var account = store.semaphoreAuthLifecycleUsers().actor();
    var session = api.semaphore().auth().loginAs(account);
    assertThat(api.semaphore().users().currentUser(session).id()).isEqualTo(account.user().id());

    api.semaphore().auth().logout(session);
    api.semaphore().auth().logout(session);
  }

  @Test
  @DisplayName("A local user changes the password only with the correct current password")
  void localUserChangesOwnPassword(
      ApiSteps api, TestStore store, SemaphoreAuthLifecycleFixtures fixtures) {
    var account = store.semaphoreAuthLifecycleUsers().actor();
    var session = api.semaphore().auth().loginAs(account);

    api.semaphore()
        .users()
        .verifyIncorrectCurrentPasswordRejected(
            session, account.user().id(), fixtures.actor().invalidCurrentPasswordChange());
    api.semaphore()
        .users()
        .changeOwnPassword(session, account.user().id(), fixtures.actor().validChange());
    api.semaphore().auth().loginIsRejected(fixtures.actor().login());

    var changedSession = api.semaphore().auth().loginAs(fixtures.actor().changedLogin());
    assertThat(api.semaphore().users().currentUser(changedSession).id())
        .isEqualTo(account.user().id());
    api.semaphore().auth().logout(changedSession);
    api.semaphore().auth().logout(session);
  }

  @Test
  @DisplayName("Known gap: a password change does not revoke another active session")
  void passwordChangeLeavesAnotherSessionAuthenticated(
      ApiSteps api, TestStore store, SemaphoreAuthLifecycleFixtures fixtures) {
    var account = store.semaphoreAuthLifecycleUsers().actor();
    var changingSession = api.semaphore().auth().loginAs(account);
    var existingSession = api.semaphore().auth().loginAs(account);

    api.semaphore()
        .users()
        .changeOwnPassword(changingSession, account.user().id(), fixtures.actor().validChange());

    assertThat(api.semaphore().users().currentUser(existingSession).id())
        .as("known gap: a pre-existing session remains authenticated after a password change")
        .isEqualTo(account.user().id());
    api.semaphore().auth().logout(existingSession);
    api.semaphore().auth().logout(changingSession);
  }

  @Test
  @DisplayName("Only an administrator can reset another local user's password")
  void administratorResetsAnotherUsersPassword(
      ApiSteps api, TestStore store, SemaphoreAuthLifecycleFixtures fixtures) {
    var actorSession = api.semaphore().auth().loginAs(store.semaphoreAuthLifecycleUsers().actor());
    var target = store.semaphoreAuthLifecycleUsers().target();

    api.semaphore()
        .users()
        .verifyCannotChangeOtherUserPassword(
            actorSession, target.user().id(), fixtures.target().administratorChange());
    api.semaphore()
        .users()
        .resetPasswordAsAdministrator(target.user().id(), fixtures.target().administratorChange());
    api.semaphore().auth().loginIsRejected(fixtures.target().login());

    var targetSession = api.semaphore().auth().loginAs(fixtures.target().changedLogin());
    assertThat(api.semaphore().users().currentUser(targetSession).id())
        .isEqualTo(target.user().id());
    api.semaphore().auth().logout(targetSession);
    api.semaphore().auth().logout(actorSession);
  }
}
