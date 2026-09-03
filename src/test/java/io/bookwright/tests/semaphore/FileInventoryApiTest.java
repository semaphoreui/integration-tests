package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreFileInventoryFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore file inventories")
class FileInventoryApiTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Task executes with an inventory file from its Git repository")
  void repositoryFileInventoryExecutes(ApiSteps api, SemaphoreFileInventoryFixtures fixtures) {
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
            .create(
                project.id(),
                fixtures.inventory().request(project.id(), key.id(), repository.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.template().request(project.id(), repository.id(), inventory.id()));
    var completedTask = api.semaphore().tasks().startAndWait(project.id(), template.id());

    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), completedTask.id(), fixtures.outputMarker());
    assertThat(inventory.type()).isEqualTo(fixtures.inventory().type());
    assertThat(inventory.inventory()).isEqualTo(fixtures.inventory().path());
    assertThat(inventory.repositoryId()).isEqualTo(repository.id());
    assertThat(completedTask.status()).isEqualTo(fixtures.successfulTaskStatus());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Known defect: create accepts a file inventory path outside its repository")
  void createAcceptsUnsafePathAlthoughUpdateRejectsIt(
      ApiSteps api, SemaphoreFileInventoryFixtures fixtures) {
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
            .create(
                project.id(),
                fixtures.unsafeInventory().request(project.id(), key.id(), repository.id()));

    assertThat(inventory.inventory()).isEqualTo(fixtures.unsafeInventory().path());
    api.semaphore()
        .inventories()
        .verifyUnsafePathUpdateRejected(
            project.id(),
            inventory.id(),
            fixtures
                .unsafeInventory()
                .updateRequest(inventory.id(), project.id(), key.id(), repository.id()));
  }
}
