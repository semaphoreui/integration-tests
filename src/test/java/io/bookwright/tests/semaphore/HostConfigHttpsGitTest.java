package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreHostConfigHttpsGitFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * A login/password credential reaches the private HTTPS Git fixture through a URL mapping while the
 * repository itself carries no key.
 */
@Api
@OwnerDanil
@Feature("Semaphore credential mappings over HTTPS")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "feature-git-https")
class HostConfigHttpsGitTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("URL mapping supplies Basic Auth for a key-less private HTTPS repository")
  void urlMappingSuppliesBasicAuth(ApiSteps api, SemaphoreHostConfigHttpsGitFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var noneKey =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.https().publicAccessKey().request(project.id()));
    var loginKey =
        api.semaphore()
            .accessKeys()
            .createAndVerifyMasked(project.id(), fixtures.https().credentials());
    var mapping =
        api.semaphore()
            .hostConfigs()
            .create(
                project.id(), fixtures.repositoryUrlMapping().request(project.id(), loginKey.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(
                project.id(), fixtures.https().repository().request(project.id(), noneKey.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.https().inventory().request(project.id(), noneKey.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.https().template().request(project.id(), repository.id(), inventory.id()));
    var completedTask = api.semaphore().tasks().startAndWait(project.id(), template.id());
    var structuredOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(
                project.id(), completedTask.id(), fixtures.https().outputMarker());
    var rawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), completedTask.id());

    assertThat(mapping.sshKeyId()).isEqualTo(loginKey.id());
    assertThat(repository.sshKeyId()).isEqualTo(noneKey.id());
    assertThat(completedTask.status()).isEqualTo(fixtures.https().successfulTaskStatus());
    assertThat(completedTask.commitHash()).isNotBlank();
    assertThat(structuredOutput).contains(fixtures.https().outputMarker());
    SecretAssertions.credentialsAbsent(
        "structured mapped HTTPS output", structuredOutput, fixtures.https().credentials());
    SecretAssertions.credentialsAbsent(
        "raw mapped HTTPS output", rawOutput, fixtures.https().credentials());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("URL mapping of another prefix does not reach the repository")
  void unmatchedUrlMappingDoesNotApply(ApiSteps api, SemaphoreHostConfigHttpsGitFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.unmatchedProject());
    var noneKey =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.https().publicAccessKey().request(project.id()));
    var loginKey =
        api.semaphore()
            .accessKeys()
            .createAndVerifyMasked(project.id(), fixtures.https().credentials());
    api.semaphore()
        .hostConfigs()
        .create(project.id(), fixtures.unmatchedUrlMapping().request(project.id(), loginKey.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(
                project.id(), fixtures.https().repository().request(project.id(), noneKey.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.https().inventory().request(project.id(), noneKey.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.https().template().request(project.id(), repository.id(), inventory.id()));
    var failedTask = api.semaphore().tasks().startAndWaitForFailure(project.id(), template.id());
    var structuredOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(
                project.id(), failedTask.id(), fixtures.https().cloneFailureMarker());
    var rawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), failedTask.id());

    assertThat(failedTask.status()).isEqualTo(fixtures.https().failedTaskStatus());
    assertThat(structuredOutput).containsIgnoringCase(fixtures.https().cloneFailureMarker());
    SecretAssertions.credentialsAbsent(
        "structured unmatched-mapping output", structuredOutput, fixtures.https().credentials());
    SecretAssertions.credentialsAbsent(
        "raw unmatched-mapping output", rawOutput, fixtures.https().credentials());
  }
}
