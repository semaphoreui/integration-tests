package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.api.model.semaphore.Template;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore shell output")
class ShellOutputTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Short-lived Bash task preserves stdout and stderr")
  void shortLivedBashTaskPreservesStdoutAndStderr(ApiSteps api, SemaphoreFixtures fixtures) {
    var template = createShellTemplate(api, fixtures, fixtures.templates().shellOutput());
    var completedTask = api.semaphore().tasks().startAndWait(template.projectId(), template.id());
    var output =
        api.semaphore().tasks().getTaskOutputText(template.projectId(), completedTask.id());

    assertOutputMarkers(output, fixtures);
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Bash task with a background child completes promptly and preserves output")
  void backgroundBashChildCompletesPromptlyAndPreservesOutput(
      ApiSteps api, SemaphoreFixtures fixtures) {
    var template = createShellTemplate(api, fixtures, fixtures.templates().backgroundShellOutput());
    long startedAt = System.nanoTime();
    var completedTask = api.semaphore().tasks().startAndWait(template.projectId(), template.id());
    var elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
    var output =
        api.semaphore().tasks().getTaskOutputText(template.projectId(), completedTask.id());

    assertThat(elapsed).isLessThan(Duration.ofSeconds(8));
    assertOutputMarkers(output, fixtures);
  }

  private Template createShellTemplate(
      ApiSteps api, SemaphoreFixtures fixtures, SemaphoreFixtures.Template templateFixture) {
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
    return api.semaphore()
        .templates()
        .create(
            project.id(), templateFixture.request(project.id(), repository.id(), inventory.id()));
  }

  private void assertOutputMarkers(String output, SemaphoreFixtures fixtures) {
    assertThat(output)
        .contains(fixtures.expectations().shellStdoutMarker())
        .contains(fixtures.expectations().shellStderrMarker());
  }
}
