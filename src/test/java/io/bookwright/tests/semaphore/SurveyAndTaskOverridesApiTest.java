package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreSurveyFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Api
@OwnerDanil
@Feature("Semaphore survey variables and task overrides")
class SurveyAndTaskOverridesApiTest {

  @Test
  @Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_PROJECT_EXISTS})
  @DisabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "prod-postgres-runner")
  @DisplayName("Survey values and launch-time overrides reach the Ansible task")
  void surveyValuesAndTaskOverridesExecute(
      ApiSteps api, TestStore store, SemaphoreFixtures core, SemaphoreSurveyFixtures fixture) {
    var project = store.semaphoreProject();
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
    var created =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixture.templateRequest(project.id(), repository.id(), inventory.id()));
    var template = api.semaphore().templates().get(project.id(), created.id());
    var task =
        api.semaphore().tasks().startAndWait(project.id(), fixture.taskRequest(template.id()));
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), task.id(), fixture.outputMarker());
    var structuredOutput = api.semaphore().tasks().getTaskOutputText(project.id(), task.id());
    var rawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), task.id());

    assertThat(template.surveyVariables()).containsExactlyElementsOf(fixture.surveyVariables());
    assertThat(template.arguments()).isEqualTo(fixture.templateArguments());
    assertThat(template.allowOverrideArgsInTask()).isTrue();
    assertThat(template.taskParameters()).isEqualTo(fixture.templateParameters());
    assertThat(task.environment()).isEqualTo(fixture.taskEnvironment());
    assertThat(task.arguments()).isEqualTo(fixture.taskArguments());
    assertThat(task.params()).isEqualTo(fixture.taskParameters());
    assertThat(task.message()).isEqualTo(fixture.taskMessage());
    assertThat(structuredOutput).contains(fixture.outputMarker());
    SecretAssertions.absent(
        "structured survey task output", structuredOutput, fixture.taskSecret().value());
    SecretAssertions.absent("raw survey task output", rawOutput, fixture.taskSecret().value());
  }

  @Test
  @Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_PROJECT_EXISTS})
  @EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "prod-postgres-runner")
  @DisplayName("Remote runner loses secret survey values before upstream fix #4086")
  void remoteRunnerLosesSurveySecret(
      ApiSteps api, TestStore store, SemaphoreFixtures core, SemaphoreSurveyFixtures fixture) {
    var project = store.semaphoreProject();
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
                fixture.templateRequest(project.id(), repository.id(), inventory.id()));
    var failed =
        api.semaphore()
            .tasks()
            .startAndWaitForFailure(project.id(), fixture.taskRequest(template.id()));
    var output = api.semaphore().tasks().getTaskOutputText(project.id(), failed.id());

    assertThat(output).contains(fixture.taskSecret().name(), "is undefined");
    SecretAssertions.absent(
        "remote runner survey task output", output, fixture.taskSecret().value());
  }

  @Test
  @Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_PROJECT_EXISTS})
  @DisplayName("Template rejects an unsupported survey variable target")
  void invalidSurveyTargetIsRejected(
      ApiSteps api, TestStore store, SemaphoreFixtures core, SemaphoreSurveyFixtures fixture) {
    var project = store.semaphoreProject();
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

    api.semaphore()
        .templates()
        .verifyRejected(
            project.id(),
            fixture.invalidTemplateRequest(project.id(), repository.id(), inventory.id()),
            fixture.expectedValidationError());
  }
}
