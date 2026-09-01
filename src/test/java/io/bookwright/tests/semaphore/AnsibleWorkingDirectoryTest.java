package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.AnsibleWorkingDirectoryFixtures;
import io.bookwright.fixtures.semaphore.AnsibleWorkingDirectoryFixtures.Scenario;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Ansible working directory")
class AnsibleWorkingDirectoryTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Working directory loads repository-local Ansible configuration and role")
  void workingDirectoryLoadsAnsibleConfiguration(
      ApiSteps api, AnsibleWorkingDirectoryFixtures fixtures) {
    execute(api, fixtures, fixtures.roleDiscovery());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Relative extra-vars path resolves from the working directory")
  void relativeExtraVarsPathResolvesFromWorkingDirectory(
      ApiSteps api, AnsibleWorkingDirectoryFixtures fixtures) {
    execute(api, fixtures, fixtures.extraVars());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Relative private-key path resolves from the working directory")
  void relativePrivateKeyPathResolvesFromWorkingDirectory(
      ApiSteps api, AnsibleWorkingDirectoryFixtures fixtures) {
    execute(api, fixtures, fixtures.privateKey());
  }

  private void execute(ApiSteps api, AnsibleWorkingDirectoryFixtures fixtures, Scenario scenario) {
    Resources resources = createResources(api, fixtures);
    var request =
        scenario.template(
            resources.projectId(),
            resources.repositoryId(),
            resources.inventoryId(),
            fixtures.workingDirectory());
    var template = api.semaphore().templates().create(resources.projectId(), request);
    var persisted =
        api.semaphore().templates().requireByName(resources.projectId(), scenario.name());
    var task = api.semaphore().tasks().startAndWait(resources.projectId(), template.id());
    var output = api.semaphore().tasks().getTaskOutputText(resources.projectId(), task.id());

    assertThat(template.workingDirectory()).isEqualTo(fixtures.workingDirectory());
    assertThat(template.arguments()).isEqualTo(scenario.arguments());
    assertThat(persisted.workingDirectory()).isEqualTo(fixtures.workingDirectory());
    assertThat(persisted.arguments()).isEqualTo(scenario.arguments());
    assertThat(output).contains(scenario.outputMarker());
  }

  private Resources createResources(ApiSteps api, AnsibleWorkingDirectoryFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var key = api.semaphore().accessKeys().create(project.id(), fixtures.accessKey(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixtures.repository(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.inventory(project.id(), key.id()));
    return new Resources(project.id(), repository.id(), inventory.id());
  }

  private record Resources(long projectId, long repositoryId, long inventoryId) {}
}
