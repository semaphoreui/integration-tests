package io.bookwright.api.semaphore;

import io.bookwright.api.model.semaphore.Inventory;
import io.bookwright.api.model.semaphore.InventoryRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SemaphoreInventoriesApi {

  @POST("project/{projectId}/inventory")
  Call<Inventory> createInventory(
      @Path("projectId") long projectId, @Body InventoryRequest request);

  @DELETE("project/{projectId}/inventory/{inventoryId}")
  Call<Void> deleteInventory(
      @Path("projectId") long projectId, @Path("inventoryId") long inventoryId);
}
