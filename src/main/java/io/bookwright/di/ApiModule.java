package io.bookwright.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.bookwright.api.RetrofitFactory;
import io.bookwright.api.local.users.UsersApi;
import io.bookwright.api.restfulbooker.health.HealthApi;
import io.bookwright.api.semaphore.accesskeys.SemaphoreAccessKeysApi;
import io.bookwright.api.semaphore.auth.SemaphoreAuthApi;
import io.bookwright.api.semaphore.inventories.SemaphoreInventoriesApi;
import io.bookwright.api.semaphore.projects.SemaphoreProjectsApi;
import io.bookwright.api.semaphore.repositories.SemaphoreRepositoriesApi;
import io.bookwright.api.semaphore.runners.SemaphoreRunnersApi;
import io.bookwright.api.semaphore.schedules.SemaphoreSchedulesApi;
import io.bookwright.api.semaphore.system.SemaphoreSystemApi;
import io.bookwright.api.semaphore.tasks.SemaphoreTasksApi;
import io.bookwright.api.semaphore.templates.SemaphoreTemplatesApi;
import io.bookwright.api.semaphore.users.SemaphoreUsersApi;
import io.bookwright.api.semaphore.variablegroups.SemaphoreVariableGroupsApi;
import io.bookwright.api.testenvironment.runners.DynamicRunnerLauncherApi;
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
    bind(io.bookwright.steps.restfulbooker.auth.AuthSteps.class).in(Singleton.class);
  }

  @Provides
  @Singleton
  @Named("restfulBooker")
  Retrofit restfulBookerRetrofit(MainConfig config) {
    return RetrofitFactory.create(config.apiBaseUrl());
  }

  @Provides
  @Singleton
  @Named("local")
  Retrofit localRetrofit(MainConfig config) {
    return RetrofitFactory.create(config.localBookingBaseUrl());
  }

  @Provides
  @Singleton
  @Named("semaphore")
  Retrofit semaphoreRetrofit(MainConfig config) {
    return RetrofitFactory.create(config.apiBaseUrl());
  }

  @Provides
  @Singleton
  @Named("runnerFixture")
  Retrofit runnerFixtureRetrofit(MainConfig config) {
    return RetrofitFactory.create(config.runnerFixtureBaseUrl());
  }

  @Provides
  @Singleton
  io.bookwright.api.restfulbooker.auth.AuthApi restfulBookerAuthApi(
      @Named("restfulBooker") Retrofit retrofit) {
    return retrofit.create(io.bookwright.api.restfulbooker.auth.AuthApi.class);
  }

  @Provides
  @Singleton
  HealthApi healthApi(@Named("restfulBooker") Retrofit retrofit) {
    return retrofit.create(HealthApi.class);
  }

  @Provides
  @Singleton
  io.bookwright.api.restfulbooker.bookings.BookingsApi restfulBookerBookingsApi(
      @Named("restfulBooker") Retrofit retrofit) {
    return retrofit.create(io.bookwright.api.restfulbooker.bookings.BookingsApi.class);
  }

  @Provides
  @Singleton
  io.bookwright.api.local.auth.AuthApi localAuthApi(@Named("local") Retrofit retrofit) {
    return retrofit.create(io.bookwright.api.local.auth.AuthApi.class);
  }

  @Provides
  @Singleton
  UsersApi usersApi(@Named("local") Retrofit retrofit) {
    return retrofit.create(UsersApi.class);
  }

  @Provides
  @Singleton
  io.bookwright.api.local.bookings.BookingsApi localBookingsApi(@Named("local") Retrofit retrofit) {
    return retrofit.create(io.bookwright.api.local.bookings.BookingsApi.class);
  }

  @Provides
  @Singleton
  SemaphoreSystemApi semaphoreSystemApi(@Named("semaphore") Retrofit retrofit) {
    return retrofit.create(SemaphoreSystemApi.class);
  }

  @Provides
  @Singleton
  SemaphoreAuthApi semaphoreAuthApi(@Named("semaphore") Retrofit retrofit) {
    return retrofit.create(SemaphoreAuthApi.class);
  }

  @Provides
  @Singleton
  SemaphoreProjectsApi semaphoreProjectsApi(@Named("semaphore") Retrofit retrofit) {
    return retrofit.create(SemaphoreProjectsApi.class);
  }

  @Provides
  @Singleton
  SemaphoreAccessKeysApi semaphoreAccessKeysApi(@Named("semaphore") Retrofit retrofit) {
    return retrofit.create(SemaphoreAccessKeysApi.class);
  }

  @Provides
  @Singleton
  SemaphoreRepositoriesApi semaphoreRepositoriesApi(@Named("semaphore") Retrofit retrofit) {
    return retrofit.create(SemaphoreRepositoriesApi.class);
  }

  @Provides
  @Singleton
  SemaphoreRunnersApi semaphoreRunnersApi(@Named("semaphore") Retrofit retrofit) {
    return retrofit.create(SemaphoreRunnersApi.class);
  }

  @Provides
  @Singleton
  SemaphoreInventoriesApi semaphoreInventoriesApi(@Named("semaphore") Retrofit retrofit) {
    return retrofit.create(SemaphoreInventoriesApi.class);
  }

  @Provides
  @Singleton
  SemaphoreTemplatesApi semaphoreTemplatesApi(@Named("semaphore") Retrofit retrofit) {
    return retrofit.create(SemaphoreTemplatesApi.class);
  }

  @Provides
  @Singleton
  SemaphoreTasksApi semaphoreTasksApi(@Named("semaphore") Retrofit retrofit) {
    return retrofit.create(SemaphoreTasksApi.class);
  }

  @Provides
  @Singleton
  SemaphoreSchedulesApi semaphoreSchedulesApi(@Named("semaphore") Retrofit retrofit) {
    return retrofit.create(SemaphoreSchedulesApi.class);
  }

  @Provides
  @Singleton
  SemaphoreUsersApi semaphoreUsersApi(@Named("semaphore") Retrofit retrofit) {
    return retrofit.create(SemaphoreUsersApi.class);
  }

  @Provides
  @Singleton
  SemaphoreVariableGroupsApi semaphoreVariableGroupsApi(@Named("semaphore") Retrofit retrofit) {
    return retrofit.create(SemaphoreVariableGroupsApi.class);
  }

  @Provides
  @Singleton
  DynamicRunnerLauncherApi dynamicRunnerLauncherApi(@Named("runnerFixture") Retrofit retrofit) {
    return retrofit.create(DynamicRunnerLauncherApi.class);
  }
}
