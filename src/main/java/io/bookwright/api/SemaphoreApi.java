package io.bookwright.api;

import io.bookwright.api.model.semaphore.AccessKey;
import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.Inventory;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.LoginRequest;
import io.bookwright.api.model.semaphore.Project;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.ProjectRole;
import io.bookwright.api.model.semaphore.Repository;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.Schedule;
import io.bookwright.api.model.semaphore.ScheduleRequest;
import io.bookwright.api.model.semaphore.Task;
import io.bookwright.api.model.semaphore.TaskOutput;
import io.bookwright.api.model.semaphore.TaskRequest;
import io.bookwright.api.model.semaphore.Template;
import io.bookwright.api.model.semaphore.TemplateRequest;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SemaphoreApi {

  @GET("ping")
  Call<ResponseBody> ping();

  @POST("auth/login")
  Call<Void> login(@Body LoginRequest request);

  @POST("projects")
  Call<Project> createProject(@Body ProjectRequest request);

  @GET("project/{projectId}")
  Call<Project> getProject(@Path("projectId") long projectId);

  @GET("project/{projectId}/role")
  Call<ProjectRole> getProjectRole(@Path("projectId") long projectId);

  @DELETE("project/{projectId}")
  Call<Void> deleteProject(@Path("projectId") long projectId);

  @POST("project/{projectId}/keys")
  Call<AccessKey> createAccessKey(
      @Path("projectId") long projectId, @Body AccessKeyRequest request);

  @DELETE("project/{projectId}/keys/{keyId}")
  Call<Void> deleteAccessKey(@Path("projectId") long projectId, @Path("keyId") long keyId);

  @POST("project/{projectId}/repositories")
  Call<Repository> createRepository(
      @Path("projectId") long projectId, @Body RepositoryRequest request);

  @DELETE("project/{projectId}/repositories/{repositoryId}")
  Call<Void> deleteRepository(
      @Path("projectId") long projectId, @Path("repositoryId") long repositoryId);

  @POST("project/{projectId}/inventory")
  Call<Inventory> createInventory(
      @Path("projectId") long projectId, @Body InventoryRequest request);

  @DELETE("project/{projectId}/inventory/{inventoryId}")
  Call<Void> deleteInventory(
      @Path("projectId") long projectId, @Path("inventoryId") long inventoryId);

  @POST("project/{projectId}/templates")
  Call<Template> createTemplate(@Path("projectId") long projectId, @Body TemplateRequest request);

  @DELETE("project/{projectId}/templates/{templateId}")
  Call<Void> deleteTemplate(@Path("projectId") long projectId, @Path("templateId") long templateId);

  @POST("project/{projectId}/tasks")
  Call<Task> startTask(@Path("projectId") long projectId, @Body TaskRequest request);

  @GET("project/{projectId}/tasks/{taskId}")
  Call<Task> getTask(@Path("projectId") long projectId, @Path("taskId") long taskId);

  @GET("project/{projectId}/tasks/{taskId}/output")
  Call<List<TaskOutput>> getTaskOutput(
      @Path("projectId") long projectId, @Path("taskId") long taskId);

  @DELETE("project/{projectId}/tasks/{taskId}")
  Call<Void> deleteTask(@Path("projectId") long projectId, @Path("taskId") long taskId);

  @POST("project/{projectId}/schedules")
  Call<Schedule> createSchedule(@Path("projectId") long projectId, @Body ScheduleRequest request);

  @GET("project/{projectId}/schedules")
  Call<List<Schedule>> getSchedules(@Path("projectId") long projectId);

  @GET("project/{projectId}/schedules/{scheduleId}")
  Call<Schedule> getSchedule(
      @Path("projectId") long projectId, @Path("scheduleId") long scheduleId);

  @DELETE("project/{projectId}/schedules/{scheduleId}")
  Call<Void> deleteSchedule(@Path("projectId") long projectId, @Path("scheduleId") long scheduleId);
}
