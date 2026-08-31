package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreVariableGroupFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore Variable Groups")
class VariableGroupApiTest {

  @Test
  @Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_PROJECT_EXISTS})
  @DisplayName("Mixed Variable Group values execute while secrets stay masked")
  void mixedVariablesExecuteWithoutSecretExposure(
      ApiSteps api,
      TestStore store,
      SemaphoreFixtures core,
      SemaphoreVariableGroupFixtures fixture) {
    var project = store.semaphoreProject();
    var createRequest = fixture.createRequest(project.id());
    var group = api.semaphore().variableGroups().createAndVerifyMasked(project.id(), createRequest);
    var renamed =
        api.semaphore()
            .variableGroups()
            .updateAndVerifyMasked(
                project.id(),
                group.id(),
                fixture.renameRequest(project.id(), group),
                createRequest);
    var key =
        api.semaphore().accessKeys().create(project.id(), core.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), core.repositories().primary().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), core.inventory().request(project.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixture.templateRequest(
                    project.id(), repository.id(), inventory.id(), renamed.id()));
    var task = api.semaphore().tasks().startAndWait(project.id(), template.id());
    var structuredOutput = api.semaphore().tasks().getTaskOutputText(project.id(), task.id());
    var rawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), task.id());

    assertThat(renamed.json()).isEqualTo(fixture.json());
    assertThat(renamed.env()).isEqualTo(fixture.env());
    assertThat(renamed.secrets())
        .extracting(secret -> secret.name())
        .containsExactlyInAnyOrder(fixture.renamedVariable(), fixture.environmentSecret().name());
    assertThat(structuredOutput).contains(fixture.outputMarker());
    SecretAssertions.absent(
        "structured Variable Group task output",
        structuredOutput,
        fixture.variableSecret().value());
    SecretAssertions.absent(
        "structured Variable Group task output",
        structuredOutput,
        fixture.environmentSecret().value());
    SecretAssertions.absent(
        "raw Variable Group task output", rawOutput, fixture.variableSecret().value());
    SecretAssertions.absent(
        "raw Variable Group task output", rawOutput, fixture.environmentSecret().value());
  }

  @Test
  @Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_PROJECT_EXISTS})
  @DisplayName("Variable Group rejects an environment variable with an empty name")
  void emptyEnvironmentVariableNameIsRejected(
      ApiSteps api, TestStore store, SemaphoreVariableGroupFixtures fixture) {
    api.semaphore()
        .variableGroups()
        .emptyEnvironmentNameIsRejected(
            store.semaphoreProject().id(),
            fixture.invalidEmptyEnvironmentNameRequest(store.semaphoreProject().id()),
            fixture.expectedValidationError());
  }
}
