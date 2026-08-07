package io.bookwright.api.semaphore;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;

public interface SemaphoreSystemApi {

  @GET("ping")
  Call<ResponseBody> ping();
}
