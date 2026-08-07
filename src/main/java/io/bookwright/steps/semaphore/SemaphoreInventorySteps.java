package io.bookwright.steps.semaphore;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Inventory;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.semaphore.SemaphoreInventoriesApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.util.UUID;

public class SemaphoreInventorySteps {

  private final SemaphoreInventoriesApi api;
  private final TeardownStorage teardown;

  @Inject
  public SemaphoreInventorySteps(SemaphoreInventoriesApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Create static inventory in Semaphore project {projectId}")
  public Inventory createStaticInventory(long projectId, long keyId) {
    Inventory inventory =
        Calls.body(
            api.createInventory(
                projectId,
                new InventoryRequest(
                    "bookwright-localhost-inventory-" + UUID.randomUUID(),
                    projectId,
                    "[local]\nlocalhost ansible_connection=local",
                    keyId,
                    "static")),
            201,
            "created inventory");
    teardown.push(
        "Delete Semaphore inventory " + inventory.id(),
        () -> Calls.expectStatus(api.deleteInventory(projectId, inventory.id()), 204));
    return inventory;
  }
}
