package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
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
class TaskMutationIsolationApiTest {
  @ParameterizedTest(name = "{0}")
  @EnumSource(ProjectRoute.class)
  @DisplayName("Foreign template launch is rejected without creating a task in either project")
  void foreignTemplateCannotBeLaunched(ProjectRoute route, ApiSteps api, TestStore store) {
    var session = loginOwner(api, store);
    var template = store.semaphoreTemplate();
    var ownProject = store.semaphoreAccessibleProject();

    api.semaphore()
        .tasks()
        .verifyLaunchHidden(
            session, route.projectId(ownProject.id(), template.projectId()), template.id());
    assertThat(api.semaphore().tasks().getTasks(template.projectId())).isEmpty();
    assertThat(api.semaphore().tasks().getTasks(ownProject.id())).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(ProjectRoute.class)
  @DisplayName("Cross-project stop is rejected and the running task completes normally")
  void foreignTaskCannotBeStopped(
      ProjectRoute route, ApiSteps api, TestStore store, SemaphoreFixtures fixtures) {
    var session = loginOwner(api, store);
    var source = store.semaphoreTemplate();
    var template =
        api.semaphore()
            .templates()
            .create(
                source.projectId(),
                fixtures
                    .templates()
                    .longRunning()
                    .request(source.projectId(), source.repositoryId(), source.inventoryId()));
    var task = api.semaphore().tasks().startTask(template.projectId(), template.id());
    api.semaphore().tasks().awaitCompletionAfterTest(task.projectId(), task.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(
            task.projectId(), task.id(), fixtures.expectations().stopReadyMarker());
    assertThat(api.semaphore().tasks().getTask(task.projectId(), task.id()).status())
        .isEqualTo("running");

    api.semaphore()
        .tasks()
        .verifyStopHidden(
            session,
            route.projectId(store.semaphoreAccessibleProject().id(), task.projectId()),
            task.id(),
            route.expectedTaskStatus());

    assertThat(api.semaphore().tasks().waitUntilTaskSucceeds(task.projectId(), task.id()).status())
        .isEqualTo("success");
    assertThat(api.semaphore().tasks().getTaskOutputText(task.projectId(), task.id()))
        .contains(fixtures.expectations().stopCompletedMarker());
  }

  private SemaphoreSessionApis loginOwner(ApiSteps api, TestStore store) {
    var session = api.semaphore().auth().loginAs(store.semaphoreIsolationUser());
    assertThat(api.semaphore().users().currentUser(session).admin()).isFalse();
    api.semaphore().users().verifyProjectReadable(session, store.semaphoreAccessibleProject().id());
    return session;
  }
}
