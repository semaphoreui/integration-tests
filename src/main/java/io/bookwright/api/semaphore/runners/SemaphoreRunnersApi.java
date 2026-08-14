package io.bookwright.api.semaphore.runners;

import io.bookwright.api.model.semaphore.Runner;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;

public interface SemaphoreRunnersApi {

  @GET("runners")
  Call<List<Runner>> getRunners();
}
