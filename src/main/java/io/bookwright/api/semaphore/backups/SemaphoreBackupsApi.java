package io.bookwright.api.semaphore.backups;

import com.fasterxml.jackson.databind.JsonNode;
import io.bookwright.api.model.semaphore.Project;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SemaphoreBackupsApi {

  @GET("project/{projectId}/backup")
  Call<JsonNode> getProjectBackup(@Path("projectId") long projectId);

  @POST("projects/restore")
  Call<Project> restoreProject(@Body JsonNode backup);
}
