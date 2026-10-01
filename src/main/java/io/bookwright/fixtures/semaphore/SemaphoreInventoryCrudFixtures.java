package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.InventoryUpdateRequest;
import io.bookwright.util.TestData;

/** Static inventory read/update/delete data. */
public record SemaphoreInventoryCrudFixtures(
    String name, String updatedName, String content, String updatedContent, String type) {

  public static SemaphoreInventoryCrudFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreInventoryCrudFixtures(
        "bookwright-crud-inventory-" + suffix,
        "bookwright-crud-inventory-updated-" + suffix,
        "[initial]\nlocalhost ansible_connection=local",
        "[updated]\nlocalhost ansible_connection=local",
        "static");
  }

  public InventoryRequest request(long projectId, long keyId) {
    return new InventoryRequest(name, projectId, content, keyId, type);
  }

  public InventoryUpdateRequest updateRequest(long projectId, long inventoryId, long keyId) {
    return new InventoryUpdateRequest(
        inventoryId, updatedName, projectId, updatedContent, keyId, null, null, type);
  }
}
