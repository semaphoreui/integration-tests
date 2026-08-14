package io.bookwright.api.semaphore.accesskeys;

import com.fasterxml.jackson.databind.JsonNode;
import io.bookwright.api.model.semaphore.AccessKey;
import io.bookwright.api.model.semaphore.AccessKeyRequest;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SemaphoreAccessKeysApi {

  @GET("project/{projectId}/keys")
  Call<List<AccessKey>> getAccessKeys(@Path("projectId") long projectId);

  @POST("project/{projectId}/keys")
  Call<AccessKey> createAccessKey(
      @Path("projectId") long projectId, @Body AccessKeyRequest request);

  @POST("project/{projectId}/keys")
  Call<JsonNode> createAccessKeyDocument(
      @Path("projectId") long projectId, @Body AccessKeyRequest request);

  @GET("project/{projectId}/keys/{keyId}")
  Call<JsonNode> getAccessKeyDocument(@Path("projectId") long projectId, @Path("keyId") long keyId);

  @GET("project/{projectId}/keys")
  Call<JsonNode> getAccessKeysDocument(@Path("projectId") long projectId);

  @DELETE("project/{projectId}/keys/{keyId}")
  Call<Void> deleteAccessKey(@Path("projectId") long projectId, @Path("keyId") long keyId);
}
