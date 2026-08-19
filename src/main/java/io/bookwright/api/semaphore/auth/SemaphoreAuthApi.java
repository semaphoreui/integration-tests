package io.bookwright.api.semaphore.auth;

import io.bookwright.api.model.semaphore.LoginRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface SemaphoreAuthApi {

  @POST("auth/login")
  Call<Void> login(@Body LoginRequest request);

  @POST("auth/logout")
  Call<Void> logout();
}
