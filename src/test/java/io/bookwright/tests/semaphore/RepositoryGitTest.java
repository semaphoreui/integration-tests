package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore Git repositories")
class RepositoryGitTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Task can clone and run a configured Git branch")
  void configuredBranchCanRunTask(ApiSteps api, SemaphoreFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.projects().git());
    var key =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(
                project.id(),
                fixtures.repositories().alternateBranch().request(project.id(), key.id()));
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
                    .primary()
                    .request(project.id(), repository.id(), inventory.id()));
    var completedTask = api.semaphore().tasks().startAndWait(project.id(), template.id());

    assertThat(repository.gitBranch())
        .isEqualTo(fixtures.repositories().alternateBranch().gitBranch());
    assertThat(completedTask.status()).isEqualTo(fixtures.expectations().successfulTaskStatus());
    assertThat(completedTask.commitHash()).isNotBlank();
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Task reports a missing Git branch as a clone failure")
  void missingBranchFailsWithUsefulOutput(ApiSteps api, SemaphoreFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.projects().git());
    var key =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(
                project.id(),
                fixtures.repositories().missingBranch().request(project.id(), key.id()));
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
                    .primary()
                    .request(project.id(), repository.id(), inventory.id()));
    var failedTask = api.semaphore().tasks().startAndWaitForFailure(project.id(), template.id());

    assertThat(repository.gitBranch())
        .isEqualTo(fixtures.repositories().missingBranch().gitBranch());
    assertThat(failedTask.status()).isEqualTo(fixtures.expectations().failedTaskStatus());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(
            project.id(), failedTask.id(), fixtures.expectations().cloneFailureMarker());
    assertThat(api.semaphore().tasks().getTaskOutputText(project.id(), failedTask.id()))
        .containsIgnoringCase(fixtures.expectations().cloneFailureMarker());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Authenticated clone failure does not expose Git credentials")
  void authenticatedCloneFailureDoesNotExposeCredentials(ApiSteps api, SemaphoreFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.projects().git());
    var key =
        api.semaphore()
            .accessKeys()
            .createAndVerifyMasked(project.id(), fixtures.secretAccessKey());
    var repository =
        api.semaphore()
            .repositories()
            .create(
                project.id(),
                fixtures.repositories().unavailable().request(project.id(), key.id()));
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
                    .primary()
                    .request(project.id(), repository.id(), inventory.id()));
    var failedTask = api.semaphore().tasks().startAndWaitForFailure(project.id(), template.id());
    var output = api.semaphore().tasks().getTaskOutputText(project.id(), failedTask.id());

    assertThat(failedTask.status()).isEqualTo(fixtures.expectations().failedTaskStatus());
    assertThat(output).containsIgnoringCase(fixtures.expectations().cloneFailureMarker());
    SecretAssertions.credentialsAbsent(
        "structured task output", output, fixtures.secretAccessKey());
    SecretAssertions.credentialsAbsent(
        "raw task output",
        api.semaphore().tasks().getTaskRawOutput(project.id(), failedTask.id()),
        fixtures.secretAccessKey());
  }
}
