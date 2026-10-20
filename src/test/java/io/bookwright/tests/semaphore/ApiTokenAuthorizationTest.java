package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreTokenFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore API-token authorization")
@Preconditions({
  Precondition.SEMAPHORE_ADMIN_SESSION,
  Precondition.SEMAPHORE_PROJECT_ISOLATION_ACTOR_EXISTS,
  Precondition.SEMAPHORE_PROJECT_EXISTS
})
class ApiTokenAuthorizationTest {

  @Test
  @DisplayName("A non-admin bearer token cannot read an unassigned project")
  void tokenKeepsProjectBoundary(ApiSteps api, TestStore store, SemaphoreTokenFixtures fixture) {
    var owner = api.semaphore().auth().loginAs(store.semaphoreIsolationUser());
    var token = api.semaphore().tokens().create(owner, fixture.validRequest());
    var bearer = api.semaphore().tokens().authenticate(token);

    assertThat(token.userId()).isEqualTo(store.semaphoreIsolationUser().user().id());
    assertThat(api.semaphore().users().currentUser(bearer).admin()).isFalse();
    api.semaphore().users().verifyProjectReadable(bearer, store.semaphoreAccessibleProject().id());
    api.semaphore().users().verifyProjectHidden(bearer, store.semaphoreProject().id());
  }

  @Test
  @DisplayName("A guest bearer token cannot create project access keys")
  void tokenKeepsGuestPermissions(
      ApiSteps api,
      TestStore store,
      SemaphoreTokenFixtures tokenFixture,
      SemaphoreFixtures fixtures) {
    var project = store.semaphoreProject();
    api.semaphore()
        .users()
        .addToProject(
            project.id(), store.semaphoreIsolationUser().user().id(), fixtures.rbac().guestRole());
    var owner = api.semaphore().auth().loginAs(store.semaphoreIsolationUser());
    var bearer =
        api.semaphore()
            .tokens()
            .authenticate(api.semaphore().tokens().create(owner, tokenFixture.validRequest()));

    api.semaphore().users().verifyProjectReadable(bearer, project.id());
    var existingKeys = api.semaphore().accessKeys().getKeys(project.id());
    api.semaphore()
        .accessKeys()
        .verifyCannotCreate(
            bearer, project.id(), fixtures.rbac().forbiddenAccessKey().request(project.id()));
    assertThat(api.semaphore().accessKeys().getKeys(project.id()))
        .containsExactlyInAnyOrderElementsOf(existingKeys);
  }

  @Test
  @DisplayName("Removing project membership immediately revokes an existing bearer token's access")
  void membershipRevocationAppliesToExistingToken(
      ApiSteps api, TestStore store, SemaphoreTokenFixtures fixture) {
    var owner = api.semaphore().auth().loginAs(store.semaphoreIsolationUser());
    var bearer =
        api.semaphore()
            .tokens()
            .authenticate(api.semaphore().tokens().create(owner, fixture.validRequest()));
    api.semaphore().users().verifyProjectReadable(bearer, store.semaphoreAccessibleProject().id());

    api.semaphore()
        .users()
        .removeFromProject(
            store.semaphoreAccessibleProject().id(), store.semaphoreIsolationUser().user().id());

    api.semaphore().users().verifyProjectHidden(bearer, store.semaphoreAccessibleProject().id());
    assertThat(api.semaphore().users().currentUser(bearer).id())
        .isEqualTo(store.semaphoreIsolationUser().user().id());
  }
}
