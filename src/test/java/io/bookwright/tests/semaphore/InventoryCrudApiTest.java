package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreInventoryCrudFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore Inventory CRUD")
@Preconditions({
  Precondition.SEMAPHORE_ADMIN_SESSION,
  Precondition.SEMAPHORE_PROJECT_EXISTS,
  Precondition.SEMAPHORE_ACCESS_KEY_EXISTS
})
class InventoryCrudApiTest {

  @Test
  @DisplayName("Static inventory can be read, updated and deleted")
  void inventoryLifecycle(ApiSteps api, TestStore store, SemaphoreInventoryCrudFixtures fixture) {
    long projectId = store.semaphoreProject().id();
    long keyId = store.semaphoreAccessKey().id();
    var inventory =
        api.semaphore().inventories().create(projectId, fixture.request(projectId, keyId));
    assertThat(api.semaphore().inventories().get(projectId, inventory.id())).isEqualTo(inventory);

    assertThat(
            api.semaphore()
                .inventories()
                .update(
                    projectId,
                    inventory.id(),
                    fixture.updateRequest(projectId, inventory.id(), keyId)))
        .satisfies(
            saved -> {
              assertThat(saved.id()).isEqualTo(inventory.id());
              assertThat(saved.projectId()).isEqualTo(projectId);
              assertThat(saved.name()).isEqualTo(fixture.updatedName());
              assertThat(saved.inventory()).isEqualTo(fixture.updatedContent());
              assertThat(saved.type()).isEqualTo(fixture.type());
              assertThat(saved.sshKeyId()).isEqualTo(keyId);
            });

    api.semaphore().inventories().delete(projectId, inventory.id());
    api.semaphore().inventories().verifyAbsent(projectId, inventory.id());
  }
}
