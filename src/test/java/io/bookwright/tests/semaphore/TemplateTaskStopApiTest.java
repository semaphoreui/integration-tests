package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreConcurrencyFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Api
@OwnerDanil
@Feature("Semaphore task lifecycle")
@Preconditions({
  Precondition.SEMAPHORE_ADMIN_SESSION,
  Precondition.SEMAPHORE_PROJECT_EXISTS,
  Precondition.SEMAPHORE_EXECUTABLE_TEMPLATE_EXISTS
})
@Isolated("Needs two simultaneously running tasks on the shared runner")
class TemplateTaskStopApiTest {

  @Test
  @DisplayName("Stop all tasks stops both running executions of a template")
  void allRunningTemplateTasksAreStopped(
      ApiSteps api, TestStore store, SemaphoreConcurrencyFixtures fixture, SemaphoreFixtures core) {
    var baseline = store.semaphoreTemplate();
    var template =
        api.semaphore()
            .templates()
            .create(
                baseline.projectId(),
                fixture.templateRequest(
                    baseline.projectId(), baseline.repositoryId(), baseline.inventoryId()));
    var first = api.semaphore().tasks().startTask(template.projectId(), template.id());
    var second = api.semaphore().tasks().startTask(template.projectId(), template.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(template.projectId(), first.id(), fixture.runningMarker());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(template.projectId(), second.id(), fixture.runningMarker());
    assertThat(api.semaphore().tasks().getTask(template.projectId(), first.id()).status())
        .isEqualTo(fixture.runningStatus());
    assertThat(api.semaphore().tasks().getTask(template.projectId(), second.id()).status())
        .isEqualTo(fixture.runningStatus());

    api.semaphore().templates().stopAllTasks(template.projectId(), template.id(), false);

    assertThat(
            api.semaphore().tasks().waitUntilTaskStops(template.projectId(), first.id()).status())
        .isEqualTo(fixture.stoppedStatus());
    assertThat(
            api.semaphore().tasks().waitUntilTaskStops(template.projectId(), second.id()).status())
        .isEqualTo(fixture.stoppedStatus());
    assertThat(api.semaphore().tasks().getTaskOutputText(template.projectId(), first.id()))
        .doesNotContain(core.expectations().stopCompletedMarker());
    assertThat(api.semaphore().tasks().getTaskOutputText(template.projectId(), second.id()))
        .doesNotContain(core.expectations().stopCompletedMarker());
  }
}
