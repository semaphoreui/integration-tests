package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreHostConfigSshFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Credential mappings applied to real Git and Ansible connections. Every repository and inventory
 * here carries a key of type {@code none}, so a successful task proves the mapping supplied the
 * credential.
 */
@Api
@OwnerDanil
@Feature("Semaphore credential mappings over SSH")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "feature-ssh-local")
class HostConfigSshTaskTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Host mapping supplies the SSH credential for Git clone, branch listing and Ansible")
  void hostMappingSuppliesCredentialForGitAndAnsible(
      ApiSteps api, SemaphoreHostConfigSshFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var noneKey =
        api.semaphore().accessKeys().create(project.id(), fixtures.noneKey().request(project.id()));
    var sshKey =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.ssh().validKey());
    var mapping =
        api.semaphore()
            .hostConfigs()
            .create(project.id(), fixtures.primaryHostMapping().request(project.id(), sshKey.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixtures.primaryRepository().request(project.id(), noneKey.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.primaryTarget().request(project.id(), noneKey.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.ssh().template().request(project.id(), repository.id(), inventory.id()));
    var branches = api.semaphore().repositories().getBranches(project.id(), repository.id());
    var completedTask = api.semaphore().tasks().startAndWait(project.id(), template.id());
    var structuredOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(
                project.id(), completedTask.id(), fixtures.ssh().outputMarker());
    var rawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), completedTask.id());

    assertThat(repository.sshKeyId()).isEqualTo(noneKey.id());
    assertThat(inventory.sshKeyId()).isEqualTo(noneKey.id());
    assertThat(mapping.sshKeyId()).isEqualTo(sshKey.id());
    assertThat(branches).contains(fixtures.primaryRepository().gitBranch());
    assertThat(completedTask.status()).isEqualTo(fixtures.ssh().successfulTaskStatus());
    assertThat(completedTask.commitHash()).isNotBlank();
    assertThat(structuredOutput).contains(fixtures.ssh().outputMarker());
    assertThat(rawOutput).contains(fixtures.ssh().outputMarker());
    SecretAssertions.absent(
        "structured mapped task output", structuredOutput, fixtures.ssh().validKey());
    SecretAssertions.absent("raw mapped task output", rawOutput, fixtures.ssh().validKey());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Two host mappings select a different credential for each host of one task")
  void mappingsSelectCredentialPerHost(ApiSteps api, SemaphoreHostConfigSshFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var noneKey =
        api.semaphore().accessKeys().create(project.id(), fixtures.noneKey().request(project.id()));
    var primaryKey =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.ssh().validKey());
    var rotatedKey =
        api.semaphore()
            .accessKeys()
            .createAndVerifyMasked(project.id(), fixtures.ssh().rotatedKey());
    api.semaphore()
        .hostConfigs()
        .create(project.id(), fixtures.primaryHostMapping().request(project.id(), primaryKey.id()));
    api.semaphore()
        .hostConfigs()
        .create(project.id(), fixtures.rotatedHostMapping().request(project.id(), rotatedKey.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixtures.primaryRepository().request(project.id(), noneKey.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.rotatedTarget().request(project.id(), noneKey.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.ssh().template().request(project.id(), repository.id(), inventory.id()));
    var completedTask = api.semaphore().tasks().startAndWait(project.id(), template.id());
    var structuredOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(
                project.id(), completedTask.id(), fixtures.ssh().outputMarker());
    var rawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), completedTask.id());

    assertThat(completedTask.status()).isEqualTo(fixtures.ssh().successfulTaskStatus());
    assertThat(structuredOutput).contains(fixtures.ssh().outputMarker());
    assertThat(rawOutput).contains(fixtures.ssh().outputMarker());
    SecretAssertions.absent(
        "structured two-host output", structuredOutput, fixtures.ssh().validKey());
    SecretAssertions.absent(
        "structured two-host output", structuredOutput, fixtures.ssh().rotatedKey());
    SecretAssertions.absent("raw two-host output", rawOutput, fixtures.ssh().validKey());
    SecretAssertions.absent("raw two-host output", rawOutput, fixtures.ssh().rotatedKey());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("URL mapping rewrites an HTTPS repository URL to SSH with the mapped key")
  void urlMappingRewritesHttpsRepositoryToSsh(
      ApiSteps api, SemaphoreHostConfigSshFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var noneKey =
        api.semaphore().accessKeys().create(project.id(), fixtures.noneKey().request(project.id()));
    var sshKey =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.ssh().validKey());
    api.semaphore()
        .hostConfigs()
        .create(project.id(), fixtures.repositoryUrlMapping().request(project.id(), sshKey.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(
                project.id(), fixtures.rewrittenRepository().request(project.id(), noneKey.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.localhost().request(project.id(), noneKey.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.ssh().template().request(project.id(), repository.id(), inventory.id()));
    var completedTask = api.semaphore().tasks().startAndWait(project.id(), template.id());
    var structuredOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(
                project.id(), completedTask.id(), fixtures.ssh().outputMarker());
    var rawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), completedTask.id());

    assertThat(repository.gitUrl()).isEqualTo(fixtures.rewrittenRepository().gitUrl());
    assertThat(completedTask.status()).isEqualTo(fixtures.ssh().successfulTaskStatus());
    assertThat(completedTask.commitHash()).isNotBlank();
    assertThat(structuredOutput).contains(fixtures.ssh().outputMarker());
    SecretAssertions.absent(
        "structured rewritten clone output", structuredOutput, fixtures.ssh().validKey());
    SecretAssertions.absent("raw rewritten clone output", rawOutput, fixtures.ssh().validKey());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("A mapped credential the host rejects fails the clone without exposing the key")
  void rejectedMappedCredentialFailsSafely(ApiSteps api, SemaphoreHostConfigSshFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var noneKey =
        api.semaphore().accessKeys().create(project.id(), fixtures.noneKey().request(project.id()));
    var wrongKey =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixtures.ssh().validKey());
    api.semaphore()
        .hostConfigs()
        .create(project.id(), fixtures.rotatedHostMapping().request(project.id(), wrongKey.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixtures.rotatedRepository().request(project.id(), noneKey.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.localhost().request(project.id(), noneKey.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.ssh().template().request(project.id(), repository.id(), inventory.id()));
    var failedTask = api.semaphore().tasks().startAndWaitForFailure(project.id(), template.id());
    var structuredOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(
                project.id(), failedTask.id(), fixtures.ssh().cloneFailureMarker());
    var rawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), failedTask.id());

    assertThat(failedTask.status()).isEqualTo(fixtures.ssh().failedTaskStatus());
    assertThat(structuredOutput).containsIgnoringCase(fixtures.ssh().cloneFailureMarker());
    SecretAssertions.absent(
        "structured rejected-mapping output", structuredOutput, fixtures.ssh().validKey());
    SecretAssertions.absent("raw rejected-mapping output", rawOutput, fixtures.ssh().validKey());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Without a mapping a key-less repository has no credential for the SSH host")
  void unmappedHostGetsNoCredential(ApiSteps api, SemaphoreHostConfigSshFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var noneKey =
        api.semaphore().accessKeys().create(project.id(), fixtures.noneKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixtures.primaryRepository().request(project.id(), noneKey.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.localhost().request(project.id(), noneKey.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.ssh().template().request(project.id(), repository.id(), inventory.id()));
    var failedTask = api.semaphore().tasks().startAndWaitForFailure(project.id(), template.id());

    assertThat(failedTask.status()).isEqualTo(fixtures.ssh().failedTaskStatus());
    assertThat(
            api.semaphore()
                .tasks()
                .waitUntilTaskOutputContains(
                    project.id(), failedTask.id(), fixtures.ssh().cloneFailureMarker()))
        .containsIgnoringCase(fixtures.ssh().cloneFailureMarker());
  }
}
