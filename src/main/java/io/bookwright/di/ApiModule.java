package io.bookwright.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.bookwright.api.AuthApi;
import io.bookwright.api.BookingApi;
import io.bookwright.api.LocalBookingApi;
import io.bookwright.api.LocalUserApi;
import io.bookwright.api.RetrofitFactory;
import io.bookwright.api.semaphore.SemaphoreAccessKeysApi;
import io.bookwright.api.semaphore.SemaphoreAuthApi;
import io.bookwright.api.semaphore.SemaphoreInventoriesApi;
import io.bookwright.api.semaphore.SemaphoreProjectsApi;
import io.bookwright.api.semaphore.SemaphoreRepositoriesApi;
import io.bookwright.api.semaphore.SemaphoreSchedulesApi;
import io.bookwright.api.semaphore.SemaphoreSystemApi;
import io.bookwright.api.semaphore.SemaphoreTasksApi;
import io.bookwright.api.semaphore.SemaphoreTemplatesApi;
import io.bookwright.api.semaphore.SemaphoreUsersApi;
import io.bookwright.config.Configs;
import io.bookwright.config.MainConfig;
import io.bookwright.teardown.TeardownStorage;
import retrofit2.Retrofit;

public class ApiModule extends AbstractModule {

  private final TeardownStorage teardownStorage;

  public ApiModule(TeardownStorage teardownStorage) {
    this.teardownStorage = teardownStorage;
  }

  @Override
  protected void configure() {
    bind(MainConfig.class).toInstance(Configs.main());
    bind(TeardownStorage.class).toInstance(teardownStorage);
    bind(io.bookwright.steps.AuthApiSteps.class).in(Singleton.class);
  }

  @Provides
  @Singleton
  Retrofit retrofit(MainConfig config) {
    return RetrofitFactory.create(config.apiBaseUrl());
  }

  @Provides
  @Singleton
  AuthApi authApi(Retrofit retrofit) {
    return retrofit.create(AuthApi.class);
  }

  @Provides
  @Singleton
  SemaphoreSystemApi semaphoreSystemApi(Retrofit retrofit) {
    return retrofit.create(SemaphoreSystemApi.class);
  }

  @Provides
  @Singleton
  SemaphoreAuthApi semaphoreAuthApi(Retrofit retrofit) {
    return retrofit.create(SemaphoreAuthApi.class);
  }

  @Provides
  @Singleton
  SemaphoreProjectsApi semaphoreProjectsApi(Retrofit retrofit) {
    return retrofit.create(SemaphoreProjectsApi.class);
  }

  @Provides
  @Singleton
  SemaphoreAccessKeysApi semaphoreAccessKeysApi(Retrofit retrofit) {
    return retrofit.create(SemaphoreAccessKeysApi.class);
  }

  @Provides
  @Singleton
  SemaphoreRepositoriesApi semaphoreRepositoriesApi(Retrofit retrofit) {
    return retrofit.create(SemaphoreRepositoriesApi.class);
  }

  @Provides
  @Singleton
  SemaphoreInventoriesApi semaphoreInventoriesApi(Retrofit retrofit) {
    return retrofit.create(SemaphoreInventoriesApi.class);
  }

  @Provides
  @Singleton
  SemaphoreTemplatesApi semaphoreTemplatesApi(Retrofit retrofit) {
    return retrofit.create(SemaphoreTemplatesApi.class);
  }

  @Provides
  @Singleton
  SemaphoreTasksApi semaphoreTasksApi(Retrofit retrofit) {
    return retrofit.create(SemaphoreTasksApi.class);
  }

  @Provides
  @Singleton
  SemaphoreSchedulesApi semaphoreSchedulesApi(Retrofit retrofit) {
    return retrofit.create(SemaphoreSchedulesApi.class);
  }

  @Provides
  @Singleton
  SemaphoreUsersApi semaphoreUsersApi(Retrofit retrofit) {
    return retrofit.create(SemaphoreUsersApi.class);
  }

  @Provides
  @Singleton
  BookingApi bookingApi(Retrofit retrofit) {
    return retrofit.create(BookingApi.class);
  }

  @Provides
  @Singleton
  LocalBookingApi localBookingApi(MainConfig config) {
    return RetrofitFactory.create(config.localBookingBaseUrl()).create(LocalBookingApi.class);
  }

  @Provides
  @Singleton
  LocalUserApi localUserApi(MainConfig config) {
    return RetrofitFactory.create(config.localBookingBaseUrl()).create(LocalUserApi.class);
  }
}
