package io.bookwright.api.semaphore.tokens;

import io.bookwright.api.model.semaphore.ApiToken;
import io.bookwright.api.model.semaphore.ApiTokenRequest;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SemaphoreTokensApi {

  @POST("user/tokens")
  Call<ApiToken> create(@Body ApiTokenRequest request);

  @GET("user/tokens")
  Call<List<ApiToken>> getTokens();

  @DELETE("user/tokens/{tokenPrefix}")
  Call<Void> delete(@Path("tokenPrefix") String tokenPrefix);
}
