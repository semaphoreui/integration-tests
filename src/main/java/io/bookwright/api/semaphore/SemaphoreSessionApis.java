package io.bookwright.api.semaphore;

import io.bookwright.api.semaphore.accesskeys.SemaphoreAccessKeysApi;
import io.bookwright.api.semaphore.auth.SemaphoreAuthApi;
import io.bookwright.api.semaphore.backups.SemaphoreBackupsApi;
import io.bookwright.api.semaphore.projects.SemaphoreProjectsApi;
import io.bookwright.api.semaphore.schedules.SemaphoreSchedulesApi;
import io.bookwright.api.semaphore.tasks.SemaphoreTasksApi;
import io.bookwright.api.semaphore.tokens.SemaphoreTokensApi;
import io.bookwright.api.semaphore.users.SemaphoreUsersApi;
import retrofit2.Retrofit;

/** Domain clients that share one isolated cookie or bearer authentication context. */
public record SemaphoreSessionApis(
    SemaphoreAuthApi auth,
    SemaphoreBackupsApi backups,
    SemaphoreProjectsApi projects,
    SemaphoreAccessKeysApi accessKeys,
    SemaphoreSchedulesApi schedules,
    SemaphoreTasksApi tasks,
    SemaphoreUsersApi users,
    SemaphoreTokensApi tokens) {

  public static SemaphoreSessionApis create(Retrofit retrofit) {
    return new SemaphoreSessionApis(
        retrofit.create(SemaphoreAuthApi.class),
        retrofit.create(SemaphoreBackupsApi.class),
        retrofit.create(SemaphoreProjectsApi.class),
        retrofit.create(SemaphoreAccessKeysApi.class),
        retrofit.create(SemaphoreSchedulesApi.class),
        retrofit.create(SemaphoreTasksApi.class),
        retrofit.create(SemaphoreUsersApi.class),
        retrofit.create(SemaphoreTokensApi.class));
  }
}
