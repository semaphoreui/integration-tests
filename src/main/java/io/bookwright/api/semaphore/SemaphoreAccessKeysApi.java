package io.bookwright.api.semaphore;

import io.bookwright.api.model.semaphore.AccessKey;
import io.bookwright.api.model.semaphore.AccessKeyRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SemaphoreAccessKeysApi {

  @POST("project/{projectId}/keys")
  Call<AccessKey> createAccessKey(
      @Path("projectId") long projectId, @Body AccessKeyRequest request);

  @DELETE("project/{projectId}/keys/{keyId}")
  Call<Void> deleteAccessKey(@Path("projectId") long projectId, @Path("keyId") long keyId);
}
