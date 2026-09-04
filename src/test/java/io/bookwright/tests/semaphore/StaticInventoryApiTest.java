package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreStaticInventoryFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore static inventories")
class StaticInventoryApiTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("INI and YAML static inventories execute only their selected group")
  void staticInventoryFormatsExecuteSelectedGroup(
      ApiSteps api, SemaphoreStaticInventoryFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var key =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixtures.repository().request(project.id(), key.id()));
    var iniInventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.iniInventory().request(project.id(), key.id()));
    var iniTemplate =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.iniTemplate().request(project.id(), repository.id(), iniInventory.id()));
    var iniTask = api.semaphore().tasks().startAndWait(project.id(), iniTemplate.id());
    var yamlInventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.yamlInventory().request(project.id(), key.id()));
    var yamlTemplate =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.yamlTemplate().request(project.id(), repository.id(), yamlInventory.id()));
    var yamlTask = api.semaphore().tasks().startAndWait(project.id(), yamlTemplate.id());
    var iniOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(project.id(), iniTask.id(), fixtures.outputMarker());
    var yamlOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(project.id(), yamlTask.id(), fixtures.outputMarker());

    assertThat(iniOutput)
        .contains(fixtures.outputMarker(), fixtures.iniInventory().selectedHost())
        .doesNotContain(fixtures.iniInventory().excludedHost());
    assertThat(yamlOutput)
        .contains(fixtures.outputMarker(), fixtures.yamlInventory().selectedHost())
        .doesNotContain(fixtures.yamlInventory().excludedHost());
    assertThat(iniInventory.inventory()).isEqualTo(fixtures.iniInventory().content());
    assertThat(iniInventory.type()).isEqualTo(fixtures.iniInventory().type());
    assertThat(yamlInventory.inventory()).isEqualTo(fixtures.yamlInventory().content());
    assertThat(yamlInventory.type()).isEqualTo(fixtures.yamlInventory().type());
  }
}
