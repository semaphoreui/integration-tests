package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore task lifecycle")
class TaskStopTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Running task can be stopped gracefully")
  void runningTaskCanBeStopped(ApiSteps api, SemaphoreFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.projects().primary());
    var key =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(
                project.id(), fixtures.repositories().primary().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.inventory().request(project.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures
                    .templates()
                    .longRunning()
                    .request(project.id(), repository.id(), inventory.id()));
    var startedTask = api.semaphore().tasks().startTask(project.id(), template.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(
            project.id(), startedTask.id(), fixtures.expectations().stopReadyMarker());
    var stoppedTask = api.semaphore().tasks().stopAndWait(project.id(), startedTask.id(), false);

    assertThat(stoppedTask.status()).isEqualTo(fixtures.expectations().stoppedTaskStatus());
    assertThat(api.semaphore().tasks().getTaskOutputText(project.id(), stoppedTask.id()))
        .doesNotContain(fixtures.expectations().stopCompletedMarker());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Running task can be force-stopped")
  void runningTaskCanBeForceStopped(ApiSteps api, SemaphoreFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.projects().primary());
    var key =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(
                project.id(), fixtures.repositories().primary().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.inventory().request(project.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures
                    .templates()
                    .longRunning()
                    .request(project.id(), repository.id(), inventory.id()));
    var startedTask = api.semaphore().tasks().startTask(project.id(), template.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(
            project.id(), startedTask.id(), fixtures.expectations().stopReadyMarker());
    var stoppedTask = api.semaphore().tasks().stopAndWait(project.id(), startedTask.id(), true);

    assertThat(stoppedTask.status()).isEqualTo(fixtures.expectations().stoppedTaskStatus());
    assertThat(api.semaphore().tasks().getTaskOutputText(project.id(), stoppedTask.id()))
        .doesNotContain(fixtures.expectations().stopCompletedMarker());
  }
}
