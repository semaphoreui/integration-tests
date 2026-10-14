package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreHostConfigGitFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreSshFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * A key-less template repository over HTTP with three submodules: a private one over HTTPS whose
 * Basic Auth comes only from a URL mapping, an anonymous one over plain HTTP, and one over SSH
 * whose key comes only from a Host mapping. The markers printed by the playbook are read from files
 * inside the checked out submodules.
 */
@Api
@OwnerDanil
@Feature("Semaphore credential mappings for Git submodules")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "feature-host-config")
class HostConfigSubmoduleGitTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("URL and Host mappings check out the HTTPS and SSH submodules next to an HTTP one")
  void mappingsCheckOutEverySubmodule(
      ApiSteps api, SemaphoreHostConfigGitFixtures fixtures, SemaphoreSshFixtures ssh) {
    var scenario = prepare(api, fixtures, ssh, true, true);
    var completedTask =
        api.semaphore().tasks().startAndWait(scenario.projectId(), scenario.templateId());
    var structuredOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(
                scenario.projectId(), completedTask.id(), fixtures.submoduleMarkers().getLast());
    var rawOutput =
        api.semaphore().tasks().getTaskRawOutput(scenario.projectId(), completedTask.id());

    assertThat(completedTask.status()).isEqualTo(fixtures.successfulTaskStatus());
    assertThat(completedTask.commitHash()).isNotBlank();
    assertThat(structuredOutput).contains(fixtures.submoduleMarkers());
    assertThat(rawOutput).contains(fixtures.submoduleMarkers());
    SecretAssertions.credentialsAbsent(
        "structured submodule output", structuredOutput, fixtures.privateCredentials());
    SecretAssertions.credentialsAbsent(
        "raw submodule output", rawOutput, fixtures.privateCredentials());
    SecretAssertions.absent("structured submodule output", structuredOutput, ssh.validKey());
    SecretAssertions.absent("raw submodule output", rawOutput, ssh.validKey());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Without the URL mapping the private HTTPS submodule fails the clone")
  void httpsSubmoduleWithoutUrlMappingFailsClone(
      ApiSteps api, SemaphoreHostConfigGitFixtures fixtures, SemaphoreSshFixtures ssh) {
    verifyCloneFails(
        api,
        fixtures,
        prepare(api, fixtures, ssh, false, true),
        fixtures.httpsSubmoduleRejection());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Without the Host mapping the SSH submodule fails the clone")
  void sshSubmoduleWithoutHostMappingFailsClone(
      ApiSteps api, SemaphoreHostConfigGitFixtures fixtures, SemaphoreSshFixtures ssh) {
    verifyCloneFails(
        api, fixtures, prepare(api, fixtures, ssh, true, false), fixtures.sshSubmoduleRejection());
  }

  private Scenario prepare(
      ApiSteps api,
      SemaphoreHostConfigGitFixtures fixtures,
      SemaphoreSshFixtures ssh,
      boolean withUrlMapping,
      boolean withHostMapping) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var noneKey =
        api.semaphore().accessKeys().create(project.id(), fixtures.noneKey().request(project.id()));
    if (withUrlMapping) {
      var loginKey =
          api.semaphore()
              .accessKeys()
              .createAndVerifyMasked(project.id(), fixtures.privateCredentials());
      api.semaphore()
          .hostConfigs()
          .create(project.id(), fixtures.privateUrlMapping().request(project.id(), loginKey.id()));
    }
    if (withHostMapping) {
      var sshKey = api.semaphore().accessKeys().createAndVerifyMasked(project.id(), ssh.validKey());
      api.semaphore()
          .hostConfigs()
          .create(project.id(), fixtures.gitSshHostMapping().request(project.id(), sshKey.id()));
    }
    var repository =
        api.semaphore()
            .repositories()
            .create(
                project.id(), fixtures.submodulesOverHttp().request(project.id(), noneKey.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.localhost().request(project.id(), noneKey.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.submodules().request(project.id(), repository.id(), inventory.id()));
    assertThat(repository.sshKeyId()).isEqualTo(noneKey.id());
    return new Scenario(project.id(), template.id());
  }

  private void verifyCloneFails(
      ApiSteps api, SemaphoreHostConfigGitFixtures fixtures, Scenario scenario, String reason) {
    var failedTask =
        api.semaphore().tasks().startAndWaitForFailure(scenario.projectId(), scenario.templateId());
    var structuredOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(
                scenario.projectId(), failedTask.id(), fixtures.cloneFailureMarker());

    assertThat(failedTask.status()).isEqualTo(fixtures.failedTaskStatus());
    assertThat(structuredOutput).contains(fixtures.cloneFailureMarker());
    assertThat(structuredOutput).contains(reason);
    assertThat(structuredOutput).doesNotContain(fixtures.submoduleMarkers());
    SecretAssertions.credentialsAbsent(
        "failed submodule output", structuredOutput, fixtures.privateCredentials());
  }

  private record Scenario(long projectId, long templateId) {}
}
