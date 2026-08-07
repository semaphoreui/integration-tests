package io.bookwright.api.semaphore.projects;

import io.bookwright.api.model.semaphore.Project;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.ProjectRole;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SemaphoreProjectsApi {

  @POST("projects")
  Call<Project> createProject(@Body ProjectRequest request);

  @GET("project/{projectId}")
  Call<Project> getProject(@Path("projectId") long projectId);

  @GET("project/{projectId}/role")
  Call<ProjectRole> getProjectRole(@Path("projectId") long projectId);

  @DELETE("project/{projectId}")
  Call<Void> deleteProject(@Path("projectId") long projectId);
}
