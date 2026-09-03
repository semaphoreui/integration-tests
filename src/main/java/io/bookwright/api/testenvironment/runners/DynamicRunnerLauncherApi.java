package io.bookwright.api.testenvironment.runners;

import io.bookwright.api.model.testenvironment.DynamicRunnerState;
import retrofit2.Call;
import retrofit2.http.GET;

public interface DynamicRunnerLauncherApi {

  @GET("state")
  Call<DynamicRunnerState> getState();
}
