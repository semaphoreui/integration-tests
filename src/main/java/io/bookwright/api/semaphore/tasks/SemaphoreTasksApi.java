package io.bookwright.api.semaphore.tasks;

import io.bookwright.api.model.semaphore.Task;
import io.bookwright.api.model.semaphore.TaskOutput;
import io.bookwright.api.model.semaphore.TaskRequest;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SemaphoreTasksApi {

  @POST("project/{projectId}/tasks")
  Call<Task> startTask(@Path("projectId") long projectId, @Body TaskRequest request);

  @GET("project/{projectId}/tasks/{taskId}")
  Call<Task> getTask(@Path("projectId") long projectId, @Path("taskId") long taskId);

  @GET("project/{projectId}/tasks/{taskId}/output")
  Call<List<TaskOutput>> getTaskOutput(
      @Path("projectId") long projectId, @Path("taskId") long taskId);

  @DELETE("project/{projectId}/tasks/{taskId}")
  Call<Void> deleteTask(@Path("projectId") long projectId, @Path("taskId") long taskId);
}
