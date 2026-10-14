package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreStaticInventoryFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreStaticInventoryFixtures.Format;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Api
@OwnerDanil
@Feature("Semaphore static inventories")
class StaticInventoryApiTest {

  @ParameterizedTest(name = "{0}")
  @EnumSource(Format.class)
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Static inventory executes only its selected group")
  void staticInventoryFormatsExecuteSelectedGroup(
      Format format, ApiSteps api, SemaphoreStaticInventoryFixtures fixtures) {
    var inventoryData = fixtures.inventory(format);
    var project = api.semaphore().projects().createProject(fixtures.project());
    var key =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixtures.repository().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), inventoryData.request(project.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.template(format).request(project.id(), repository.id(), inventory.id()));
    var task = api.semaphore().tasks().startAndWait(project.id(), template.id());
    var output =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(project.id(), task.id(), fixtures.outputMarker());

    assertThat(output)
        .contains(fixtures.outputMarker(), inventoryData.selectedHost())
        .doesNotContain(inventoryData.excludedHost());
    assertThat(inventory.inventory()).isEqualTo(inventoryData.content());
    assertThat(inventory.type()).isEqualTo(inventoryData.type());
  }
}
