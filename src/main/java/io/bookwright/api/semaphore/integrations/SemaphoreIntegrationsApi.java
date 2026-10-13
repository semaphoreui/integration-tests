package io.bookwright.api.semaphore.integrations;

import io.bookwright.api.model.semaphore.Integration;
import io.bookwright.api.model.semaphore.IntegrationAlias;
import io.bookwright.api.model.semaphore.IntegrationExtractValue;
import io.bookwright.api.model.semaphore.IntegrationExtractValueRequest;
import io.bookwright.api.model.semaphore.IntegrationMatcher;
import io.bookwright.api.model.semaphore.IntegrationMatcherRequest;
import io.bookwright.api.model.semaphore.IntegrationRequest;
import io.bookwright.api.model.semaphore.IntegrationUpdateRequest;
import java.util.List;
import java.util.Map;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Url;

public interface SemaphoreIntegrationsApi {

  @GET("project/{projectId}/integrations")
  Call<List<Integration>> getIntegrations(@Path("projectId") long projectId);

  @POST("project/{projectId}/integrations")
  Call<Integration> create(@Path("projectId") long projectId, @Body IntegrationRequest request);

  @GET("project/{projectId}/integrations/{integrationId}")
  Call<Integration> get(
      @Path("projectId") long projectId, @Path("integrationId") long integrationId);

  @PUT("project/{projectId}/integrations/{integrationId}")
  Call<Void> update(
      @Path("projectId") long projectId,
      @Path("integrationId") long integrationId,
      @Body IntegrationUpdateRequest request);

  @DELETE("project/{projectId}/integrations/{integrationId}")
  Call<Void> delete(@Path("projectId") long projectId, @Path("integrationId") long integrationId);

  @POST("project/{projectId}/integrations/aliases")
  Call<IntegrationAlias> createProjectAlias(@Path("projectId") long projectId);

  @GET("project/{projectId}/integrations/aliases")
  Call<List<IntegrationAlias>> getProjectAliases(@Path("projectId") long projectId);

  @DELETE("project/{projectId}/integrations/aliases/{aliasId}")
  Call<Void> deleteProjectAlias(@Path("projectId") long projectId, @Path("aliasId") long aliasId);

  @POST("project/{projectId}/integrations/{integrationId}/aliases")
  Call<IntegrationAlias> createIntegrationAlias(
      @Path("projectId") long projectId, @Path("integrationId") long integrationId);

  @GET("project/{projectId}/integrations/{integrationId}/aliases")
  Call<List<IntegrationAlias>> getIntegrationAliases(
      @Path("projectId") long projectId, @Path("integrationId") long integrationId);

  @DELETE("project/{projectId}/integrations/{integrationId}/aliases/{aliasId}")
  Call<Void> deleteIntegrationAlias(
      @Path("projectId") long projectId,
      @Path("integrationId") long integrationId,
      @Path("aliasId") long aliasId);

  @GET("project/{projectId}/integrations/{integrationId}/matchers")
  Call<List<IntegrationMatcher>> getMatchers(
      @Path("projectId") long projectId, @Path("integrationId") long integrationId);

  @POST("project/{projectId}/integrations/{integrationId}/matchers")
  Call<IntegrationMatcher> addMatcher(
      @Path("projectId") long projectId,
      @Path("integrationId") long integrationId,
      @Body IntegrationMatcherRequest request);

  @PUT("project/{projectId}/integrations/{integrationId}/matchers/{matcherId}")
  Call<Void> updateMatcher(
      @Path("projectId") long projectId,
      @Path("integrationId") long integrationId,
      @Path("matcherId") long matcherId,
      @Body IntegrationMatcherRequest request);

  @DELETE("project/{projectId}/integrations/{integrationId}/matchers/{matcherId}")
  Call<Void> deleteMatcher(
      @Path("projectId") long projectId,
      @Path("integrationId") long integrationId,
      @Path("matcherId") long matcherId);

  @GET("project/{projectId}/integrations/{integrationId}/values")
  Call<List<IntegrationExtractValue>> getExtractValues(
      @Path("projectId") long projectId, @Path("integrationId") long integrationId);

  @POST("project/{projectId}/integrations/{integrationId}/values")
  Call<IntegrationExtractValue> addExtractValue(
      @Path("projectId") long projectId,
      @Path("integrationId") long integrationId,
      @Body IntegrationExtractValueRequest request);

  @PUT("project/{projectId}/integrations/{integrationId}/values/{valueId}")
  Call<Void> updateExtractValue(
      @Path("projectId") long projectId,
      @Path("integrationId") long integrationId,
      @Path("valueId") long valueId,
      @Body IntegrationExtractValueRequest request);

  @DELETE("project/{projectId}/integrations/{integrationId}/values/{valueId}")
  Call<Void> deleteExtractValue(
      @Path("projectId") long projectId,
      @Path("integrationId") long integrationId,
      @Path("valueId") long valueId);

  @POST
  Call<Void> dispatch(
      @Url String url, @HeaderMap Map<String, String> headers, @Body RequestBody payload);
}
