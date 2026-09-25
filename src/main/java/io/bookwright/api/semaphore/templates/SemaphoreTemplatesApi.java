package io.bookwright.api.semaphore.templates;

import io.bookwright.api.model.semaphore.TaskStopRequest;
import io.bookwright.api.model.semaphore.Template;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.api.model.semaphore.TemplateUpdateRequest;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface SemaphoreTemplatesApi {

  @GET("project/{projectId}/templates")
  Call<List<Template>> getTemplates(@Path("projectId") long projectId);

  @POST("project/{projectId}/templates")
  Call<Template> createTemplate(@Path("projectId") long projectId, @Body TemplateRequest request);

  @GET("project/{projectId}/templates/{templateId}")
  Call<Template> getTemplate(
      @Path("projectId") long projectId, @Path("templateId") long templateId);

  @PUT("project/{projectId}/templates/{templateId}")
  Call<Void> updateTemplate(
      @Path("projectId") long projectId,
      @Path("templateId") long templateId,
      @Body TemplateUpdateRequest request);

  @POST("project/{projectId}/templates/{templateId}/stop_all_tasks")
  Call<Void> stopAllTasks(
      @Path("projectId") long projectId,
      @Path("templateId") long templateId,
      @Body TaskStopRequest request);

  @DELETE("project/{projectId}/templates/{templateId}")
  Call<Void> deleteTemplate(@Path("projectId") long projectId, @Path("templateId") long templateId);
}
