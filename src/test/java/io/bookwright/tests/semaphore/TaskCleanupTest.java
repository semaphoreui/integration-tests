package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreConcurrencyFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.bookwright.teardown.TeardownStorage;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Api
@OwnerDanil
@Feature("Semaphore task cleanup")
@Isolated("Leaves a running task and a queued task for teardown on the shared executor")
class TaskCleanupTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Teardown stops and deletes running and queued tasks before dependent resources")
  void teardownCleansUpUnfinishedTasks(
      ApiSteps api,
      SemaphoreFixtures core,
      SemaphoreConcurrencyFixtures fixture,
      TeardownStorage teardown) {
    var project = api.semaphore().projects().createProject(fixture.projectRequest());
    var key =
        api.semaphore().accessKeys().create(project.id(), core.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), core.repositories().primary().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), core.inventory().request(project.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixture.templateRequest(project.id(), repository.id(), inventory.id()));

    // LIFO: this assertion runs after both task cleanups and before template/project deletion.
    teardown.push(
        "Verify task cleanup removed both unfinished tasks",
        () -> {
          var remaining = api.semaphore().tasks().getTasks(project.id());
          if (!remaining.isEmpty()) {
            throw new IllegalStateException(
                "Task cleanup left tasks in project %d: %s"
                    .formatted(project.id(), remaining.stream().map(task -> task.id()).toList()));
          }
        });
    var running = api.semaphore().tasks().startTask(project.id(), template.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), running.id(), fixture.runningMarker());
    var queued = api.semaphore().tasks().startTask(project.id(), template.id());
    api.semaphore()
        .tasks()
        .verifyRemainsInStatus(project.id(), queued.id(), fixture.waitingStatus());
    assertThat(api.semaphore().tasks().getTask(project.id(), running.id()).status())
        .isEqualTo(fixture.runningStatus());
    // Intentionally finish with unfinished tasks: the registered teardown owns stopping them.
  }
}
