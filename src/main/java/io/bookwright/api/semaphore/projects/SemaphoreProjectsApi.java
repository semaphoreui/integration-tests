package io.bookwright.api.semaphore.projects;

import io.bookwright.api.model.semaphore.Project;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.ProjectRole;
import io.bookwright.api.model.semaphore.ProjectUpdateRequest;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface SemaphoreProjectsApi {

  @GET("projects")
  Call<List<Project>> getProjects();

  @POST("projects")
  Call<Project> createProject(@Body ProjectRequest request);

  @GET("project/{projectId}")
  Call<Project> getProject(@Path("projectId") long projectId);

  @PUT("project/{projectId}")
  Call<Void> updateProject(@Path("projectId") long projectId, @Body ProjectUpdateRequest request);

  @GET("project/{projectId}/role")
  Call<ProjectRole> getProjectRole(@Path("projectId") long projectId);

  @DELETE("project/{projectId}")
  Call<Void> deleteProject(@Path("projectId") long projectId);
}
