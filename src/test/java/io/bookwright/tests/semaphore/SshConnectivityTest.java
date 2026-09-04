package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreSshFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Api
@OwnerDanil
@Feature("Semaphore SSH connectivity")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "feature-ssh-local")
class SshConnectivityTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Task clones Git over SSH and executes a playbook on an SSH target")
  void sshKeyWorksForRepositoryAndInventory(ApiSteps api, SemaphoreSshFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var key = api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.validKey());
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
    var structuredOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(project.id(), completedTask.id(), fixtures.outputMarker());
    var rawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), completedTask.id());

    assertThat(key.type()).isEqualTo(fixtures.validKey().type());
    assertThat(repository.sshKeyId()).isEqualTo(key.id());
    assertThat(inventory.sshKeyId()).isEqualTo(key.id());
    assertThat(completedTask.status()).isEqualTo(fixtures.successfulTaskStatus());
    assertThat(structuredOutput).contains(fixtures.outputMarker());
    assertThat(rawOutput).contains(fixtures.outputMarker());
    SecretAssertions.absent("structured SSH task output", structuredOutput, fixtures.validKey());
    SecretAssertions.absent("raw SSH task output", rawOutput, fixtures.validKey());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Invalid SSH key fails with useful output without exposing key material")
  void invalidSshKeyFailsSafely(ApiSteps api, SemaphoreSshFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var key =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.invalidKey());
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
    var structuredOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(
                project.id(), failedTask.id(), fixtures.cloneFailureMarker());
    var rawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), failedTask.id());

    assertThat(failedTask.status()).isEqualTo(fixtures.failedTaskStatus());
    assertThat(structuredOutput).containsIgnoringCase(fixtures.cloneFailureMarker());
    SecretAssertions.absent(
        "structured failed SSH task output", structuredOutput, fixtures.invalidKey());
    SecretAssertions.absent("raw failed SSH task output", rawOutput, fixtures.invalidKey());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Rotated SSH key replaces the old secret and restores task execution")
  void rotatedSshKeyIsUsedByRepositoryAndInventory(ApiSteps api, SemaphoreSshFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var key = api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.validKey());
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixtures.rotatedRepository().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.rotatedInventory().request(project.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.template().request(project.id(), repository.id(), inventory.id()));

    var failedTask = api.semaphore().tasks().startAndWaitForFailure(project.id(), template.id());
    var failedOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(
                project.id(), failedTask.id(), fixtures.cloneFailureMarker());
    assertThat(failedOutput).containsIgnoringCase(fixtures.cloneFailureMarker());
    SecretAssertions.absent("pre-rotation task output", failedOutput, fixtures.validKey());

    api.semaphore()
        .accessKeys()
        .rotateAndVerifyMasked(project.id(), key.id(), fixtures.rotatedKey());

    var completedTask = api.semaphore().tasks().startAndWait(project.id(), template.id());
    var structuredOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(project.id(), completedTask.id(), fixtures.outputMarker());
    var rawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), completedTask.id());

    assertThat(completedTask.status()).isEqualTo(fixtures.successfulTaskStatus());
    assertThat(structuredOutput).contains(fixtures.outputMarker());
    assertThat(rawOutput).contains(fixtures.outputMarker());
    SecretAssertions.absent("rotated SSH task output", structuredOutput, fixtures.validKey());
    SecretAssertions.absent("rotated SSH task output", structuredOutput, fixtures.rotatedKey());
    SecretAssertions.absent("rotated SSH raw output", rawOutput, fixtures.validKey());
    SecretAssertions.absent("rotated SSH raw output", rawOutput, fixtures.rotatedKey());
  }
}
