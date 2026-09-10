package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.api.model.semaphore.Template;
import io.bookwright.fixtures.semaphore.SemaphoreProcessCleanupFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Api
@OwnerDanil
@Feature("Semaphore local process cleanup")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "core-sqlite-local")
class LocalProcessCleanupTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Normal task completion cleans up a background descendant")
  void normalCompletionCleansUpBackgroundDescendant(
      ApiSteps api, SemaphoreProcessCleanupFixtures fixtures) {
    var templates = createTemplates(api, fixtures, fixtures.templates().normalCompletion());

    long startedAt = System.nanoTime();
    var completedTask =
        api.semaphore().tasks().startAndWait(templates.main().projectId(), templates.main().id());
    var elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
    var mainOutput =
        api.semaphore().tasks().getTaskOutputText(templates.main().projectId(), completedTask.id());

    var verificationOutput = verifyCleanup(api, fixtures, templates, completedTask.id());

    assertThat(completedTask.status()).isEqualTo(fixtures.expectations().successfulTaskStatus());
    assertThat(elapsed).isLessThan(fixtures.expectations().maximumCompletionTime());
    assertThat(mainOutput)
        .contains(fixtures.expectations().stdoutMarker())
        .contains(fixtures.expectations().stderrMarker());
    assertThat(verificationOutput).contains(fixtures.expectations().resistantProcessGoneMarker());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Graceful task stop sends SIGTERM to the process group")
  void gracefulStopSendsTermToProcessGroup(ApiSteps api, SemaphoreProcessCleanupFixtures fixtures) {
    var templates = createTemplates(api, fixtures, fixtures.templates().gracefulStop());
    var task =
        api.semaphore().tasks().startTask(templates.main().projectId(), templates.main().id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(
            templates.main().projectId(), task.id(), fixtures.expectations().termReadyMarker());

    long stoppedAt = System.nanoTime();
    var stoppedTask =
        api.semaphore().tasks().stopAndWait(templates.main().projectId(), task.id(), false);
    var stopElapsed = Duration.ofNanos(System.nanoTime() - stoppedAt);
    var taskOutput =
        api.semaphore().tasks().getTaskOutputText(templates.main().projectId(), stoppedTask.id());
    var verificationOutput = verifyCleanup(api, fixtures, templates, stoppedTask.id());

    assertThat(stoppedTask.status()).isEqualTo(fixtures.expectations().stoppedTaskStatus());
    assertThat(stopElapsed).isLessThan(fixtures.expectations().maximumGracefulStopTime());
    assertThat(taskOutput)
        .contains(fixtures.expectations().mainTermMarker())
        .contains(fixtures.expectations().childTermMarker());
    assertThat(verificationOutput)
        .contains(fixtures.expectations().gracefulDescendantsGoneMarker());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Resistant process group is killed after the grace period")
  void resistantProcessGroupIsKilledAfterGracePeriod(
      ApiSteps api, SemaphoreProcessCleanupFixtures fixtures) {
    var templates = createTemplates(api, fixtures, fixtures.templates().resistantStop());
    var task =
        api.semaphore().tasks().startTask(templates.main().projectId(), templates.main().id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(
            templates.main().projectId(),
            task.id(),
            fixtures.expectations().resistantStopReadyMarker());

    long stoppedAt = System.nanoTime();
    var stoppedTask =
        api.semaphore().tasks().stopAndWait(templates.main().projectId(), task.id(), false);
    var stopElapsed = Duration.ofNanos(System.nanoTime() - stoppedAt);
    var taskOutput =
        api.semaphore().tasks().getTaskOutputText(templates.main().projectId(), stoppedTask.id());
    var verificationOutput = verifyCleanup(api, fixtures, templates, stoppedTask.id());

    assertThat(stoppedTask.status()).isEqualTo(fixtures.expectations().stoppedTaskStatus());
    assertThat(stopElapsed)
        .isGreaterThanOrEqualTo(fixtures.expectations().minimumEscalationTime())
        .isLessThan(fixtures.expectations().maximumEscalationTime());
    assertThat(taskOutput)
        .contains(fixtures.expectations().resistantStopMainTermMarker())
        .contains(fixtures.expectations().resistantStopChildTermMarker());
    assertThat(verificationOutput)
        .contains(fixtures.expectations().resistantStopProcessesGoneMarker());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Descendant can escape cleanup by changing its process group")
  void descendantCanEscapeCleanupByChangingProcessGroup(
      ApiSteps api, SemaphoreProcessCleanupFixtures fixtures) {
    var templates = createTemplates(api, fixtures, fixtures.templates().escapedProcessGroup());

    long startedAt = System.nanoTime();
    var completedTask =
        api.semaphore().tasks().startAndWait(templates.main().projectId(), templates.main().id());
    var elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
    var taskOutput =
        api.semaphore().tasks().getTaskOutputText(templates.main().projectId(), completedTask.id());
    var verificationOutput = verifyCleanup(api, fixtures, templates, completedTask.id());

    assertThat(completedTask.status()).isEqualTo(fixtures.expectations().successfulTaskStatus());
    assertThat(elapsed).isLessThan(fixtures.expectations().maximumCompletionTime());
    assertThat(taskOutput).contains(fixtures.expectations().escapedProcessReadyMarker());
    assertThat(verificationOutput).contains(fixtures.expectations().escapedProcessAliveMarker());
  }

  private String verifyCleanup(
      ApiSteps api,
      SemaphoreProcessCleanupFixtures fixtures,
      CreatedTemplates templates,
      long completedTaskId) {
    var verificationTask =
        api.semaphore()
            .tasks()
            .startAndWait(
                templates.verifier().projectId(),
                fixtures.verificationRequest(templates.verifier().id(), completedTaskId));
    return api.semaphore()
        .tasks()
        .getTaskOutputText(templates.verifier().projectId(), verificationTask.id());
  }

  private CreatedTemplates createTemplates(
      ApiSteps api,
      SemaphoreProcessCleanupFixtures fixtures,
      SemaphoreProcessCleanupFixtures.Scenario scenario) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var key =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixtures.repository().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.inventory().request(project.id(), key.id()));
    var main =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                scenario.main().request(project.id(), repository.id(), inventory.id()));
    var verifier =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                scenario.verifier().request(project.id(), repository.id(), inventory.id()));
    return new CreatedTemplates(main, verifier);
  }

  private record CreatedTemplates(Template main, Template verifier) {}
}
