package io.bookwright.tests.ui.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.OwnerDanil;
import io.bookwright.annotations.SensitiveUi;
import io.bookwright.annotations.Ui;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreVariableGroupFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.bookwright.steps.UiSteps;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Ui
@SensitiveUi
@OwnerDanil
@Feature("Semaphore Variable Groups")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "core-sqlite-local")
class SemaphoreVariableGroupUiTest {

  @Test
  @Issue("https://github.com/semaphoreui/semaphore/issues/2293")
  @Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_PROJECT_EXISTS})
  @DisplayName("Renamed Variable Group secret persists and keeps its value")
  void renamedSecretPersistsAndExecutes(
      ApiSteps api,
      UiSteps ui,
      TestStore store,
      SemaphoreFixtures core,
      SemaphoreVariableGroupFixtures fixture) {
    var project = store.semaphoreProject();
    var createRequest = fixture.createRequest(project.id());
    var group = api.semaphore().variableGroups().createAndVerifyMasked(project.id(), createRequest);
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
                fixture.templateRequest(project.id(), repository.id(), inventory.id(), group.id()));

    ui.semaphore().core().login();
    ui.semaphore()
        .variableGroups()
        .renameSecret(
            project.id(), group, fixture.variableSecret().name(), fixture.renamedVariable());

    assertThat(
            api.semaphore()
                .variableGroups()
                .getAndVerifyMasked(project.id(), group.id(), createRequest)
                .secrets())
        .extracting(secret -> secret.name())
        .containsExactlyInAnyOrder(fixture.renamedVariable(), fixture.environmentSecret().name());

    var task = api.semaphore().tasks().startAndWait(project.id(), template.id());
    var structuredOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(project.id(), task.id(), fixture.outputMarker());
    var rawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), task.id());

    assertThat(structuredOutput).contains(fixture.outputMarker());
    SecretAssertions.absent(
        "structured Variable Group UI task output",
        structuredOutput,
        fixture.variableSecret().value());
    SecretAssertions.absent(
        "structured Variable Group UI task output",
        structuredOutput,
        fixture.environmentSecret().value());
    SecretAssertions.absent(
        "raw Variable Group UI task output", rawOutput, fixture.variableSecret().value());
    SecretAssertions.absent(
        "raw Variable Group UI task output", rawOutput, fixture.environmentSecret().value());
  }
}
