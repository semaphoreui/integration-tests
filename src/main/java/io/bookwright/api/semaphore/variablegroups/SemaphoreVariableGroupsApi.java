package io.bookwright.api.semaphore.variablegroups;

import com.fasterxml.jackson.databind.JsonNode;
import io.bookwright.api.model.semaphore.VariableGroupRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface SemaphoreVariableGroupsApi {

  @POST("project/{projectId}/environment")
  Call<JsonNode> create(@Path("projectId") long projectId, @Body VariableGroupRequest request);

  @GET("project/{projectId}/environment")
  Call<JsonNode> getAll(@Path("projectId") long projectId);

  @GET("project/{projectId}/environment/{groupId}")
  Call<JsonNode> get(@Path("projectId") long projectId, @Path("groupId") long groupId);

  @PUT("project/{projectId}/environment/{groupId}")
  Call<Void> update(
      @Path("projectId") long projectId,
      @Path("groupId") long groupId,
      @Body VariableGroupRequest request);

  @DELETE("project/{projectId}/environment/{groupId}")
  Call<Void> delete(@Path("projectId") long projectId, @Path("groupId") long groupId);
}
