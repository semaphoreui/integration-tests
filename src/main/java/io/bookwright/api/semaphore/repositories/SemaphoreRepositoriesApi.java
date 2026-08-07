package io.bookwright.api.semaphore.repositories;

import io.bookwright.api.model.semaphore.Repository;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SemaphoreRepositoriesApi {

  @POST("project/{projectId}/repositories")
  Call<Repository> createRepository(
      @Path("projectId") long projectId, @Body RepositoryRequest request);

  @DELETE("project/{projectId}/repositories/{repositoryId}")
  Call<Void> deleteRepository(
      @Path("projectId") long projectId, @Path("repositoryId") long repositoryId);
}
