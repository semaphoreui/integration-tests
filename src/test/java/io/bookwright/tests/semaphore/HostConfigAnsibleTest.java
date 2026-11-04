package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreHostConfigGitFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreSshFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreSshFixtures.Repository;
import io.bookwright.fixtures.semaphore.SemaphoreSshFixtures.Template;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Credential mappings consumed by Ansible itself: the SSH connection to a target container, the
 * {@code ansible.builtin.git} module on the controller and a role from {@code
 * roles/requirements.yml}. Every repository and inventory carries a key of type {@code none}.
 */
@Api
@OwnerDanil
@Feature("Semaphore credential mappings for Ansible")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "feature-host-config")
class HostConfigAnsibleTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Host mapping supplies the SSH credential for a playbook on the target container")
  void hostMappingSuppliesCredentialForTargetContainer(
      ApiSteps api, SemaphoreHostConfigGitFixtures fixtures, SemaphoreSshFixtures ssh) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var noneKey =
        api.semaphore().accessKeys().create(project.id(), fixtures.noneKey().request(project.id()));
    var sshKey = api.semaphore().accessKeys().createAndVerifyMasked(project.id(), ssh.validKey());
    var mapping =
        api.semaphore()
            .hostConfigs()
            .create(project.id(), fixtures.targetHostMapping().request(project.id(), sshKey.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixtures.ansible().request(project.id(), noneKey.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.target().request(project.id(), noneKey.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.targetPlaybook().request(project.id(), repository.id(), inventory.id()));
    var completedTask = api.semaphore().tasks().startAndWait(project.id(), template.id());
    var structuredOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(project.id(), completedTask.id(), fixtures.targetMarker());
    var rawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), completedTask.id());

    assertThat(mapping.sshKeyId()).isEqualTo(sshKey.id());
    assertThat(inventory.sshKeyId()).isEqualTo(noneKey.id());
    assertThat(completedTask.status()).isEqualTo(fixtures.successfulTaskStatus());
    assertThat(structuredOutput).contains(fixtures.targetMarker());
    assertThat(rawOutput).contains(fixtures.targetMarker());
    SecretAssertions.absent("structured target output", structuredOutput, ssh.validKey());
    SecretAssertions.absent("raw target output", rawOutput, ssh.validKey());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("URL mapping supplies Basic Auth to the Ansible git module")
  void urlMappingReachesAnsibleGitModule(ApiSteps api, SemaphoreHostConfigGitFixtures fixtures) {
    verifyMappedLocalPlaybook(
        api, fixtures, fixtures.ansible(), fixtures.gitModule(), fixtures.gitModuleMarker());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("URL mapping supplies Basic Auth to a private role from roles/requirements.yml")
  void urlMappingReachesGalaxyRequirements(ApiSteps api, SemaphoreHostConfigGitFixtures fixtures) {
    verifyMappedLocalPlaybook(
        api, fixtures, fixtures.galaxy(), fixtures.galaxyRole(), fixtures.galaxyRoleMarker());
  }

  private void verifyMappedLocalPlaybook(
      ApiSteps api,
      SemaphoreHostConfigGitFixtures fixtures,
      Repository source,
      Template playbook,
      String marker) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var noneKey =
        api.semaphore().accessKeys().create(project.id(), fixtures.noneKey().request(project.id()));
    var loginKey =
        api.semaphore()
            .accessKeys()
            .createAndVerifyMasked(project.id(), fixtures.privateCredentials());
    api.semaphore()
        .hostConfigs()
        .create(project.id(), fixtures.privateUrlMapping().request(project.id(), loginKey.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), source.request(project.id(), noneKey.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.localhost().request(project.id(), noneKey.id()));
    var template =
        api.semaphore()
            .templates()
            .create(project.id(), playbook.request(project.id(), repository.id(), inventory.id()));
    var completedTask = api.semaphore().tasks().startAndWait(project.id(), template.id());
    var structuredOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(project.id(), completedTask.id(), marker);
    var rawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), completedTask.id());

    assertThat(completedTask.status()).isEqualTo(fixtures.successfulTaskStatus());
    assertThat(structuredOutput).contains(marker);
    SecretAssertions.credentialsAbsent(
        "structured mapped Ansible output", structuredOutput, fixtures.privateCredentials());
    SecretAssertions.credentialsAbsent(
        "raw mapped Ansible output", rawOutput, fixtures.privateCredentials());
  }
}
