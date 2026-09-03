package io.bookwright.api.semaphore.tasks;

import io.bookwright.api.model.semaphore.Task;
import io.bookwright.api.model.semaphore.TaskOutput;
import io.bookwright.api.model.semaphore.TaskRequest;
import io.bookwright.api.model.semaphore.TaskStopRequest;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SemaphoreTasksApi {

  @GET("project/{projectId}/tasks")
  Call<List<Task>> getTasks(@Path("projectId") long projectId);

  @GET("project/{projectId}/templates/{templateId}/tasks")
  Call<List<Task>> getTemplateTasks(
      @Path("projectId") long projectId, @Path("templateId") long templateId);

  @POST("project/{projectId}/tasks")
  Call<Task> startTask(@Path("projectId") long projectId, @Body TaskRequest request);

  @GET("project/{projectId}/tasks/{taskId}")
  Call<Task> getTask(@Path("projectId") long projectId, @Path("taskId") long taskId);

  @GET("project/{projectId}/tasks/{taskId}/output")
  Call<List<TaskOutput>> getTaskOutput(
      @Path("projectId") long projectId, @Path("taskId") long taskId);

  @GET("project/{projectId}/tasks/{taskId}/raw_output")
  Call<ResponseBody> getTaskRawOutput(
      @Path("projectId") long projectId, @Path("taskId") long taskId);

  @POST("project/{projectId}/tasks/{taskId}/stop")
  Call<Void> stopTask(
      @Path("projectId") long projectId,
      @Path("taskId") long taskId,
      @Body TaskStopRequest request);

  @DELETE("project/{projectId}/tasks/{taskId}")
  Call<Void> deleteTask(@Path("projectId") long projectId, @Path("taskId") long taskId);
}
