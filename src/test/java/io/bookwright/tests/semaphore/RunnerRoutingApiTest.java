package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreRunnerRoutingFixtures;
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
@Feature("Semaphore runner routing and capacity")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "prod-postgres-runner")
@Isolated("Temporarily mutates the shared global runner configuration")
class RunnerRoutingApiTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Runner tag, availability and capacity control task dispatch")
  void runnerTagAvailabilityAndCapacityControlDispatch(
      ApiSteps api, SemaphoreFixtures core, SemaphoreRunnerRoutingFixtures fixture) {
    var runner = api.semaphore().runners().waitUntilDefaultRunnerIsOnline();
    runner =
        api.semaphore()
            .runners()
            .configureTemporarily(runner.id(), fixture.runnerRequest(runner, true));
    assertThat(api.semaphore().runners().waitUntilTagIsAvailable(fixture.matchingTag()).tag())
        .isEqualTo(fixture.matchingTag());

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
                fixture.matchingTemplateRequest(project.id(), repository.id(), inventory.id()));

    var first = api.semaphore().tasks().startTask(project.id(), template.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), first.id(), fixture.runningMarker());
    var capacityQueued = api.semaphore().tasks().startTask(project.id(), template.id());
    api.semaphore()
        .tasks()
        .verifyRemainsInStatus(project.id(), capacityQueued.id(), fixture.waitingStatus());
    assertThat(api.semaphore().tasks().stopAndWait(project.id(), first.id(), true).status())
        .isEqualTo(fixture.stoppedStatus());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), capacityQueued.id(), fixture.runningMarker());
    assertThat(
            api.semaphore()
                .tasks()
                .requirePersistedTask(project.id(), capacityQueued.id())
                .usedRunnerId())
        .isEqualTo(runner.id());
    api.semaphore().tasks().stopAndWait(project.id(), capacityQueued.id(), true);

    api.semaphore().runners().updateRunner(runner.id(), fixture.runnerRequest(runner, false));
    var unavailable = api.semaphore().tasks().startAndWaitForFailure(project.id(), template.id());
    assertThat(unavailable.status()).isEqualTo(fixture.errorStatus());
    assertThat(
            api.semaphore()
                .tasks()
                .waitUntilTaskOutputContains(
                    project.id(), unavailable.id(), fixture.unavailableDiagnostic()))
        .contains(fixture.unavailableDiagnostic());
    api.semaphore().runners().updateRunner(runner.id(), fixture.runnerRequest(runner, true));
    api.semaphore().runners().waitUntilRunnerIsOnline(runner.id());
    var recovered = api.semaphore().tasks().startTask(project.id(), template.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), recovered.id(), fixture.runningMarker());
    assertThat(
            api.semaphore()
                .tasks()
                .requirePersistedTask(project.id(), recovered.id())
                .usedRunnerId())
        .isEqualTo(runner.id());
    api.semaphore().tasks().stopAndWait(project.id(), recovered.id(), true);

    var unmatchedTemplate =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixture.missingTemplateRequest(project.id(), repository.id(), inventory.id()));
    var unmatched =
        api.semaphore().tasks().startAndWaitForFailure(project.id(), unmatchedTemplate.id());
    assertThat(unmatched.status()).isEqualTo(fixture.errorStatus());
    assertThat(
            api.semaphore()
                .tasks()
                .waitUntilTaskOutputContains(
                    project.id(), unmatched.id(), fixture.unavailableDiagnostic()))
        .contains(fixture.unavailableDiagnostic());
  }
}
