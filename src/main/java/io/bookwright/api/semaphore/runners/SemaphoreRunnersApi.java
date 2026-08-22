package io.bookwright.api.semaphore.runners;

import io.bookwright.api.model.semaphore.Runner;
import io.bookwright.api.model.semaphore.RunnerTag;
import io.bookwright.api.model.semaphore.RunnerUpdateRequest;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface SemaphoreRunnersApi {

  @GET("runners")
  Call<List<Runner>> getRunners();

  @GET("runners/{runnerId}")
  Call<Runner> getRunner(@Path("runnerId") long runnerId);

  @PUT("runners/{runnerId}")
  Call<Void> updateRunner(@Path("runnerId") long runnerId, @Body RunnerUpdateRequest request);

  @GET("runner_tags")
  Call<List<RunnerTag>> getRunnerTags();
}
