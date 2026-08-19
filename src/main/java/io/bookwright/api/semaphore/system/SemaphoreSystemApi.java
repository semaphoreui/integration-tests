package io.bookwright.api.semaphore.system;

import io.bookwright.api.model.semaphore.SemaphoreSystemInfo;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;

public interface SemaphoreSystemApi {

  @GET("ping")
  Call<ResponseBody> ping();

  @GET("info")
  Call<SemaphoreSystemInfo> info();
}
