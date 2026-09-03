package io.bookwright.api.semaphore.integrations;

import io.bookwright.api.model.semaphore.Integration;
import io.bookwright.api.model.semaphore.IntegrationAlias;
import io.bookwright.api.model.semaphore.IntegrationExtractValue;
import io.bookwright.api.model.semaphore.IntegrationExtractValueRequest;
import io.bookwright.api.model.semaphore.IntegrationMatcher;
import io.bookwright.api.model.semaphore.IntegrationMatcherRequest;
import io.bookwright.api.model.semaphore.IntegrationRequest;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Url;

public interface SemaphoreIntegrationsApi {

  @POST("project/{projectId}/integrations")
  Call<Integration> create(@Path("projectId") long projectId, @Body IntegrationRequest request);

  @DELETE("project/{projectId}/integrations/{integrationId}")
  Call<Void> delete(@Path("projectId") long projectId, @Path("integrationId") long integrationId);

  @POST("project/{projectId}/integrations/aliases")
  Call<IntegrationAlias> createProjectAlias(@Path("projectId") long projectId);

  @DELETE("project/{projectId}/integrations/aliases/{aliasId}")
  Call<Void> deleteProjectAlias(@Path("projectId") long projectId, @Path("aliasId") long aliasId);

  @POST("project/{projectId}/integrations/{integrationId}/matchers")
  Call<IntegrationMatcher> addMatcher(
      @Path("projectId") long projectId,
      @Path("integrationId") long integrationId,
      @Body IntegrationMatcherRequest request);

  @POST("project/{projectId}/integrations/{integrationId}/values")
  Call<IntegrationExtractValue> addExtractValue(
      @Path("projectId") long projectId,
      @Path("integrationId") long integrationId,
      @Body IntegrationExtractValueRequest request);

  @POST
  Call<Void> dispatch(
      @Url String url, @HeaderMap Map<String, String> headers, @Body Map<String, Object> payload);
}
