package io.bookwright.api.semaphore.auth;

import io.bookwright.api.model.semaphore.LoginRequest;
import io.bookwright.api.model.semaphore.TotpPasscodeRequest;
import io.bookwright.api.model.semaphore.TotpRecoveryRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface SemaphoreAuthApi {

  @POST("auth/login")
  Call<Void> login(@Body LoginRequest request);

  @POST("auth/verify")
  Call<Void> verifyTotp(@Body TotpPasscodeRequest request);

  @POST("auth/recovery")
  Call<Void> recoverTotp(@Body TotpRecoveryRequest request);

  @POST("auth/logout")
  Call<Void> logout();
}
