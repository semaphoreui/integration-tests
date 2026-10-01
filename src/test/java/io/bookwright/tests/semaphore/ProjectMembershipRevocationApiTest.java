package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.fixtures.semaphore.SemaphoreProjectIsolationFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore project membership revocation")
@Preconditions({
  Precondition.SEMAPHORE_ADMIN_SESSION,
  Precondition.SEMAPHORE_PROJECT_ISOLATION_ACTOR_EXISTS,
  Precondition.SEMAPHORE_PROJECT_EXISTS,
  Precondition.SEMAPHORE_EXECUTABLE_TEMPLATE_EXISTS
})
class ProjectMembershipRevocationApiTest {

  @Test
  @DisplayName("Removing membership revokes task reads in an already authenticated session")
  void existingSessionLosesReadAccess(
      ApiSteps api, TestStore store, SemaphoreProjectIsolationFixtures fixtures) {
    var template = store.semaphoreTemplate();
    var session = loginMember(api, store, fixtures);
    var task = api.semaphore().tasks().startTask(template.projectId(), template.id());
    api.semaphore().tasks().awaitCompletionAfterTest(task.projectId(), task.id());
    api.semaphore().tasks().waitUntilTaskSucceeds(task.projectId(), task.id());
    assertThat(api.semaphore().tasks().getTask(session, task.projectId(), task.id()).id())
        .isEqualTo(task.id());

    api.semaphore()
        .users()
        .removeFromProject(task.projectId(), store.semaphoreIsolationUser().user().id());

    api.semaphore().users().verifyProjectHidden(session, task.projectId());
    api.semaphore().tasks().verifyTaskHidden(session, task.projectId(), task.id(), 404);
    assertThat(api.semaphore().tasks().getTask(task.projectId(), task.id()).status())
        .isEqualTo("success");
    verifySessionStillWorks(api, store, session);
  }

  @Test
  @DisplayName("Removing membership revokes task launch without ending the user's session")
  void existingSessionLosesLaunchAccess(
      ApiSteps api, TestStore store, SemaphoreProjectIsolationFixtures fixtures) {
    var template = store.semaphoreTemplate();
    var session = loginMember(api, store, fixtures);
    var before = api.semaphore().tasks().startAndWait(session, template.projectId(), template.id());

    api.semaphore()
        .users()
        .removeFromProject(template.projectId(), store.semaphoreIsolationUser().user().id());

    api.semaphore().tasks().verifyLaunchHidden(session, template.projectId(), template.id());
    assertThat(api.semaphore().tasks().getTasks(template.projectId()))
        .extracting(task -> task.id())
        .containsExactly(before.id());
    assertThat(api.semaphore().tasks().getTasks(store.semaphoreAccessibleProject().id())).isEmpty();
    verifySessionStillWorks(api, store, session);
  }

  private SemaphoreSessionApis loginMember(
      ApiSteps api, TestStore store, SemaphoreProjectIsolationFixtures fixtures) {
    api.semaphore()
        .users()
        .addToProject(
            store.semaphoreProject().id(),
            store.semaphoreIsolationUser().user().id(),
            fixtures.role());
    var session = api.semaphore().auth().loginAs(store.semaphoreIsolationUser());
    assertThat(api.semaphore().users().currentUser(session).admin()).isFalse();
    api.semaphore().users().verifyProjectReadable(session, store.semaphoreProject().id());
    return session;
  }

  private void verifySessionStillWorks(
      ApiSteps api, TestStore store, SemaphoreSessionApis session) {
    assertThat(api.semaphore().users().currentUser(session).id())
        .isEqualTo(store.semaphoreIsolationUser().user().id());
    api.semaphore().users().verifyProjectReadable(session, store.semaphoreAccessibleProject().id());
  }
}
