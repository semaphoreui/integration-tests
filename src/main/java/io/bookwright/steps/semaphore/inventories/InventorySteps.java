package io.bookwright.steps.semaphore.inventories;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Inventory;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.semaphore.inventories.SemaphoreInventoriesApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.util.List;

public class InventorySteps {

  private final SemaphoreInventoriesApi api;
  private final TeardownStorage teardown;

  @Inject
  public InventorySteps(SemaphoreInventoriesApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Create static inventory in Semaphore project {projectId}")
  public Inventory create(long projectId, InventoryRequest request) {
    Inventory inventory =
        Calls.body(api.createInventory(projectId, request), 201, "created inventory");
    teardown.push(
        "Delete Semaphore inventory " + inventory.id(),
        () -> Calls.expectStatus(api.deleteInventory(projectId, inventory.id()), 204));
    return inventory;
  }

  @Step("Find required inventory {name} in Semaphore project {projectId}")
  public Inventory requireByName(long projectId, String name) {
    List<Inventory> inventories = Calls.body(api.getInventories(projectId), 200, "inventories");
    return inventories.stream()
        .filter(inventory -> name.equals(inventory.name()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Required inventory '%s' was not found in project %d. Available inventories: %s"
                        .formatted(
                            name, projectId, inventories.stream().map(Inventory::name).toList())));
  }
}
