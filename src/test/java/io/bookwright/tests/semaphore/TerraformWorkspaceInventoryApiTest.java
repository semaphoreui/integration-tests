package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreTerraformFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Api
@OwnerDanil
@Feature("Semaphore Terraform workspace inventories")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "core-sqlite-local")
class TerraformWorkspaceInventoryApiTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Terraform and OpenTofu plans use workspaces and masked TF_VAR secrets")
  void terraformAndTofuUseConfiguredWorkspaces(ApiSteps api, SemaphoreTerraformFixtures fixtures) {
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
    var terraformInventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.terraform().inventory().request(project.id()));
    var terraformTemplate =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures
                    .terraform()
                    .template()
                    .request(
                        project.id(),
                        repository.id(),
                        terraformInventory.id(),
                        variableGroup.id()));
    var terraformTask =
        api.semaphore()
            .tasks()
            .startAndWait(
                project.id(), fixtures.terraform().template().planRequest(terraformTemplate.id()));
    var tofuInventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.tofu().inventory().request(project.id()));
    var tofuTemplate =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures
                    .tofu()
                    .template()
                    .request(
                        project.id(), repository.id(), tofuInventory.id(), variableGroup.id()));
    var tofuTask =
        api.semaphore()
            .tasks()
            .startAndWait(project.id(), fixtures.tofu().template().planRequest(tofuTemplate.id()));

    var terraformOutput =
        api.semaphore().tasks().getTaskOutputText(project.id(), terraformTask.id());
    var tofuOutput = api.semaphore().tasks().getTaskOutputText(project.id(), tofuTask.id());
    var terraformRawOutput =
        api.semaphore().tasks().getTaskRawOutput(project.id(), terraformTask.id());
    var tofuRawOutput = api.semaphore().tasks().getTaskRawOutput(project.id(), tofuTask.id());

    assertThat(terraformOutput)
        .contains(
            fixtures.workspaceOutputName(),
            fixtures.terraform().inventory().workspace(),
            fixtures.variableGroup().outputMarker());
    assertThat(tofuOutput)
        .contains(
            fixtures.workspaceOutputName(),
            fixtures.tofu().inventory().workspace(),
            fixtures.variableGroup().outputMarker());
    SecretAssertions.absent(
        "structured Terraform task output",
        terraformOutput,
        fixtures.variableGroup().secretValue());
    SecretAssertions.absent(
        "raw Terraform task output", terraformRawOutput, fixtures.variableGroup().secretValue());
    SecretAssertions.absent(
        "structured OpenTofu task output", tofuOutput, fixtures.variableGroup().secretValue());
    SecretAssertions.absent(
        "raw OpenTofu task output", tofuRawOutput, fixtures.variableGroup().secretValue());
    assertThat(terraformInventory.type()).isEqualTo(fixtures.terraform().inventory().type());
    assertThat(tofuInventory.type()).isEqualTo(fixtures.tofu().inventory().type());
    assertThat(terraformTemplate.app()).isEqualTo(fixtures.terraform().template().app());
    assertThat(tofuTemplate.app()).isEqualTo(fixtures.tofu().template().app());
  }
}
