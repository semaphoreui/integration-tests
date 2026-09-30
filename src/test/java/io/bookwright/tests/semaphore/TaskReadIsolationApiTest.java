package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.api.model.semaphore.Task;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreProjectIsolationFixtures.ProjectRoute;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Api
@OwnerDanil
@Feature("Semaphore cross-project isolation")
@Preconditions({
  Precondition.SEMAPHORE_ADMIN_SESSION,
  Precondition.SEMAPHORE_PROJECT_ISOLATION_ACTOR_EXISTS,
  Precondition.SEMAPHORE_PROJECT_EXISTS,
  Precondition.SEMAPHORE_EXECUTABLE_TEMPLATE_EXISTS
})
class TaskReadIsolationApiTest {
  @ParameterizedTest(name = "{0}")
  @EnumSource(ProjectRoute.class)
  @DisplayName("Task details cannot be read across projects")
  void taskDetailsAreHidden(ProjectRoute route, ApiSteps api, TestStore store) {
    var session = loginOwner(api, store);
    var task = completedTask(api, store);

    api.semaphore()
        .tasks()
        .verifyTaskHidden(
            session,
            route.projectId(store.semaphoreAccessibleProject().id(), task.projectId()),
            task.id(),
            route.expectedTaskStatus());
    assertThat(api.semaphore().tasks().getTask(task.projectId(), task.id()).status())
        .isEqualTo("success");
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(ProjectRoute.class)
  @DisplayName("Structured task output cannot be read across projects")
  void structuredOutputIsHidden(
      ProjectRoute route, ApiSteps api, TestStore store, SemaphoreFixtures fixtures) {
    var session = loginOwner(api, store);
    var task = completedTask(api, store);
    assertThat(api.semaphore().tasks().getTaskOutputText(task.projectId(), task.id()))
        .contains(fixtures.expectations().outputMarker());

    api.semaphore()
        .tasks()
        .verifyOutputHidden(
            session,
            route.projectId(store.semaphoreAccessibleProject().id(), task.projectId()),
            task.id(),
            route.expectedTaskStatus());
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(ProjectRoute.class)
  @DisplayName("Raw task output cannot be read across projects")
  void rawOutputIsHidden(
      ProjectRoute route, ApiSteps api, TestStore store, SemaphoreFixtures fixtures) {
    var session = loginOwner(api, store);
    var task = completedTask(api, store);
    assertThat(api.semaphore().tasks().getTaskRawOutput(task.projectId(), task.id()))
        .contains(fixtures.expectations().outputMarker());

    api.semaphore()
        .tasks()
        .verifyRawOutputHidden(
            session,
            route.projectId(store.semaphoreAccessibleProject().id(), task.projectId()),
            task.id(),
            route.expectedTaskStatus());
  }

  private Task completedTask(ApiSteps api, TestStore store) {
    var template = store.semaphoreTemplate();
    var task = api.semaphore().tasks().startTask(template.projectId(), template.id());
    api.semaphore().tasks().awaitCompletionAfterTest(task.projectId(), task.id());
    return api.semaphore().tasks().waitUntilTaskSucceeds(task.projectId(), task.id());
  }

  private SemaphoreSessionApis loginOwner(ApiSteps api, TestStore store) {
    var session = api.semaphore().auth().loginAs(store.semaphoreIsolationUser());
    assertThat(api.semaphore().users().currentUser(session).admin()).isFalse();
    api.semaphore().users().verifyProjectReadable(session, store.semaphoreAccessibleProject().id());
    return session;
  }
}
