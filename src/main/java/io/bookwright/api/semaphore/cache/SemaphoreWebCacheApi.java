package io.bookwright.api.semaphore.cache;

import io.bookwright.api.model.semaphore.LoginRequest;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;
import retrofit2.http.Url;

/** Raw HTTP boundary used only by the shared-cache security profile. */
public interface SemaphoreWebCacheApi {

  @POST("auth/login")
  Call<Void> login(@Body LoginRequest request);

  @GET
  Call<ResponseBody> get(@Url String url, @HeaderMap Map<String, String> headers);
}
