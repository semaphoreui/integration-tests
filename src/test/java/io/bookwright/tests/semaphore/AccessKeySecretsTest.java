package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.annotations.Smoke;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreAccessKeyCrudFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@Smoke
@OwnerDanil
@Feature("Semaphore access-key security")
class AccessKeySecretsTest {

  @Test
  @Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_PROJECT_EXISTS})
  @DisplayName("Rotated login/password key remains masked in API responses and task output")
  void loginPasswordKeyIsNotExposed(
      ApiSteps api,
      TestStore store,
      SemaphoreFixtures fixtures,
      SemaphoreAccessKeyCrudFixtures credentials) {
    var project = store.semaphoreProject();
    var repositoryKey =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(
                project.id(),
                fixtures.repositories().primary().request(project.id(), repositoryKey.id()));
    var secretKey =
        api.semaphore().accessKeys().createAndVerifyMasked(project.id(), credentials.original());
    api.semaphore()
        .accessKeys()
        .updateAndVerifyMasked(
            project.id(),
            secretKey.id(),
            credentials.updateRequest(project.id(), secretKey.id()),
            credentials.rotated());
    api.semaphore().accessKeys().verifyMasked(project.id(), secretKey.id(), credentials.original());
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.inventory().request(project.id(), secretKey.id()));
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
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(
            project.id(), completedTask.id(), fixtures.expectations().outputMarker());

    assertThat(secretKey.type()).isEqualTo(credentials.original().type());
    assertThat(inventory.sshKeyId()).isEqualTo(secretKey.id());
    assertThat(completedTask.status()).isEqualTo(fixtures.expectations().successfulTaskStatus());

    var output = api.semaphore().tasks().getTaskOutputText(project.id(), completedTask.id());
    var rawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), completedTask.id());
    SecretAssertions.credentialsAbsent("structured task output", output, credentials.original());
    SecretAssertions.credentialsAbsent("structured task output", output, credentials.rotated());
    SecretAssertions.credentialsAbsent("raw task output", rawOutput, credentials.original());
    SecretAssertions.credentialsAbsent("raw task output", rawOutput, credentials.rotated());
  }
}
