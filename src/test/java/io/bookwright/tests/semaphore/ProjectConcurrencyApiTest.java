package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreConcurrencyFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Api
@OwnerDanil
@Feature("Semaphore project task concurrency")
@Isolated("Temporarily saturates the shared persistent runner")
class ProjectConcurrencyApiTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Project max parallel tasks controls queue admission")
  void projectParallelLimitControlsQueue(
      ApiSteps api, SemaphoreFixtures core, SemaphoreConcurrencyFixtures fixture) {
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

    var first = api.semaphore().tasks().startTask(project.id(), template.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), first.id(), fixture.runningMarker());
    var queued = api.semaphore().tasks().startTask(project.id(), template.id());

    assertThat(api.semaphore().tasks().getTask(project.id(), first.id()).status())
        .isEqualTo(fixture.runningStatus());
    assertThat(
            api.semaphore()
                .tasks()
                .verifyRemainsInStatus(project.id(), queued.id(), fixture.waitingStatus())
                .status())
        .isEqualTo(fixture.waitingStatus());
    assertThat(api.semaphore().tasks().getTasks(project.id()))
        .extracting(task -> task.id())
        .contains(first.id(), queued.id());

    assertThat(api.semaphore().tasks().stopAndWait(project.id(), first.id(), true).status())
        .isEqualTo(fixture.stoppedStatus());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), queued.id(), fixture.runningMarker());
    assertThat(api.semaphore().tasks().stopAndWait(project.id(), queued.id(), true).status())
        .isEqualTo(fixture.stoppedStatus());

    assertThat(
            api.semaphore()
                .projects()
                .updateProject(project.id(), fixture.parallelProjectRequest(project.id()))
                .maxParallelTasks())
        .isEqualTo(fixture.parallelLimit());
    var parallelFirst = api.semaphore().tasks().startTask(project.id(), template.id());
    var parallelSecond = api.semaphore().tasks().startTask(project.id(), template.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), parallelFirst.id(), fixture.runningMarker());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), parallelSecond.id(), fixture.runningMarker());

    assertThat(api.semaphore().tasks().getTask(project.id(), parallelFirst.id()).status())
        .isEqualTo(fixture.runningStatus());
    assertThat(api.semaphore().tasks().getTask(project.id(), parallelSecond.id()).status())
        .isEqualTo(fixture.runningStatus());
    api.semaphore().tasks().stopAndWait(project.id(), parallelFirst.id(), true);
    api.semaphore().tasks().stopAndWait(project.id(), parallelSecond.id(), true);
  }
}
