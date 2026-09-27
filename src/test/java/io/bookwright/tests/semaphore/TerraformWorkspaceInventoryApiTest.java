package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreTerraformFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreTerraformFixtures.Engine;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Api
@OwnerDanil
@Feature("Semaphore Terraform workspace inventories")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "core-sqlite-local|external")
class TerraformWorkspaceInventoryApiTest {

  @ParameterizedTest(name = "{0}")
  @EnumSource(Engine.class)
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Plan uses the configured workspace and a masked TF_VAR secret")
  void planUsesConfiguredWorkspace(
      Engine engine, ApiSteps api, SemaphoreTerraformFixtures fixtures) {
    var tool = fixtures.tool(engine);
    var project = api.semaphore().projects().createProject(fixtures.project());
    var key =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixtures.repository().request(project.id(), key.id()));
    var variableGroup =
        api.semaphore()
            .variableGroups()
            .createAndVerifyMasked(project.id(), fixtures.variableGroup().request(project.id()));
    var inventory =
        api.semaphore().inventories().create(project.id(), tool.inventory().request(project.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                tool.template()
                    .request(project.id(), repository.id(), inventory.id(), variableGroup.id()));
    var task =
        api.semaphore()
            .tasks()
            .startAndWait(project.id(), tool.template().planRequest(template.id()));

    var output =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(
                project.id(), task.id(), fixtures.variableGroup().outputMarker());

    assertThat(output)
        .contains(
            fixtures.workspaceOutputName(),
            tool.inventory().workspace(),
            fixtures.variableGroup().outputMarker());
    SecretAssertions.absent(
        "structured " + engine + " task output", output, fixtures.variableGroup().secretValue());
    SecretAssertions.absent(
        "raw " + engine + " task output",
        api.semaphore().tasks().getTaskRawOutput(project.id(), task.id()),
        fixtures.variableGroup().secretValue());
    assertThat(inventory.type()).isEqualTo(tool.inventory().type());
    assertThat(template.app()).isEqualTo(tool.template().app());
  }
}
