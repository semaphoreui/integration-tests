package io.bookwright.tests.semaphore.parallel;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreBranchIsolationFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;

@Api
@OwnerDanil
@Feature("Semaphore parallel task isolation")
@EnabledIfSystemProperty(
    named = "SEMAPHORE_PROFILE",
    matches = "(feature-parallel-tasks-cmd-git|feature-parallel-tasks-go-git)")
@Isolated("Coordinates two tasks through the local executor filesystem")
class BranchCheckoutIsolationApiTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Parallel tasks keep files from their effective Git branches")
  void parallelTasksKeepTheirEffectiveBranchFiles(
      ApiSteps api, SemaphoreBranchIsolationFixtures fixture) {
    var project = api.semaphore().projects().createProject(fixture.project());
    var key =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixture.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixture.repository().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixture.inventory().request(project.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixture.template().request(project.id(), repository.id(), inventory.id()));

    var branchA =
        api.semaphore().tasks().startTask(project.id(), fixture.branchATask(template.id()));
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), branchA.id(), fixture.branchAReadyMarker());
    var branchB =
        api.semaphore().tasks().startTask(project.id(), fixture.branchBTask(template.id()));

    assertThat(api.semaphore().tasks().waitUntilTaskSucceeds(project.id(), branchA.id()).status())
        .isEqualTo(fixture.successfulTaskStatus());
    assertThat(api.semaphore().tasks().waitUntilTaskSucceeds(project.id(), branchB.id()).status())
        .isEqualTo(fixture.successfulTaskStatus());
    assertThat(api.semaphore().tasks().getTaskOutputText(project.id(), branchA.id()))
        .contains(fixture.branchAMarker());
    assertThat(api.semaphore().tasks().getTaskOutputText(project.id(), branchB.id()))
        .contains(fixture.branchBMarker());
  }
}
