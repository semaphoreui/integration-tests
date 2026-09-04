package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreHttpsGitFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Api
@OwnerDanil
@Feature("Semaphore private HTTPS Git repositories")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "feature-git-https")
class HttpsRepositoryGitTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Task can clone and run a private HTTPS Git repository")
  void authenticatedHttpsRepositoryCanRunTask(ApiSteps api, SemaphoreHttpsGitFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.authenticatedProject());
    var key =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.credentials());
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixtures.repository().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.inventory().request(project.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.template().request(project.id(), repository.id(), inventory.id()));
    var completedTask = api.semaphore().tasks().startAndWait(project.id(), template.id());
    var output =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(project.id(), completedTask.id(), fixtures.outputMarker());

    assertThat(completedTask.status()).isEqualTo(fixtures.successfulTaskStatus());
    assertThat(completedTask.commitHash()).isNotBlank();
    assertThat(output).contains(fixtures.outputMarker());
    SecretAssertions.credentialsAbsent("structured task output", output, fixtures.credentials());
    SecretAssertions.credentialsAbsent(
        "raw task output",
        api.semaphore().tasks().getTaskRawOutput(project.id(), completedTask.id()),
        fixtures.credentials());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Private HTTPS Git repository rejects a missing access key")
  void unauthenticatedHttpsRepositoryFailsToClone(
      ApiSteps api, SemaphoreHttpsGitFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.unauthenticatedProject());
    var key =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.publicAccessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixtures.repository().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.inventory().request(project.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.template().request(project.id(), repository.id(), inventory.id()));
    var failedTask = api.semaphore().tasks().startAndWaitForFailure(project.id(), template.id());

    assertThat(failedTask.status()).isEqualTo(fixtures.failedTaskStatus());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), failedTask.id(), fixtures.cloneFailureMarker());
  }
}
