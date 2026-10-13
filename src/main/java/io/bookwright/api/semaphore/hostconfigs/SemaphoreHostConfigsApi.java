package io.bookwright.api.semaphore.hostconfigs;

import io.bookwright.api.model.semaphore.HostConfig;
import io.bookwright.api.model.semaphore.HostConfigRequest;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface SemaphoreHostConfigsApi {

  @GET("project/{projectId}/host_configs")
  Call<List<HostConfig>> getHostConfigs(@Path("projectId") long projectId);

  @POST("project/{projectId}/host_configs")
  Call<HostConfig> createHostConfig(
      @Path("projectId") long projectId, @Body HostConfigRequest request);

  @GET("project/{projectId}/host_configs/{hostConfigId}")
  Call<HostConfig> getHostConfig(
      @Path("projectId") long projectId, @Path("hostConfigId") long hostConfigId);

  @PUT("project/{projectId}/host_configs/{hostConfigId}")
  Call<Void> updateHostConfig(
      @Path("projectId") long projectId,
      @Path("hostConfigId") long hostConfigId,
      @Body HostConfigRequest request);

  @DELETE("project/{projectId}/host_configs/{hostConfigId}")
  Call<Void> deleteHostConfig(
      @Path("projectId") long projectId, @Path("hostConfigId") long hostConfigId);
}
