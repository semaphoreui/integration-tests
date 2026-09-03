package io.bookwright.api.semaphore.inventories;

import io.bookwright.api.model.semaphore.Inventory;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.InventoryUpdateRequest;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface SemaphoreInventoriesApi {

  @GET("project/{projectId}/inventory")
  Call<List<Inventory>> getInventories(@Path("projectId") long projectId);

  @POST("project/{projectId}/inventory")
  Call<Inventory> createInventory(
      @Path("projectId") long projectId, @Body InventoryRequest request);

  @PUT("project/{projectId}/inventory/{inventoryId}")
  Call<Void> updateInventory(
      @Path("projectId") long projectId,
      @Path("inventoryId") long inventoryId,
      @Body InventoryUpdateRequest request);

  @DELETE("project/{projectId}/inventory/{inventoryId}")
  Call<Void> deleteInventory(
      @Path("projectId") long projectId, @Path("inventoryId") long inventoryId);
}
