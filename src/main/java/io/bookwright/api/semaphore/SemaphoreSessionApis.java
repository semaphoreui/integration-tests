package io.bookwright.api.semaphore;

import io.bookwright.api.semaphore.accesskeys.SemaphoreAccessKeysApi;
import io.bookwright.api.semaphore.auth.SemaphoreAuthApi;
import io.bookwright.api.semaphore.backups.SemaphoreBackupsApi;
import io.bookwright.api.semaphore.projects.SemaphoreProjectsApi;
import io.bookwright.api.semaphore.schedules.SemaphoreSchedulesApi;
import io.bookwright.api.semaphore.tasks.SemaphoreTasksApi;
import io.bookwright.api.semaphore.users.SemaphoreUsersApi;

/** Domain clients that share one isolated authenticated cookie jar. */
public record SemaphoreSessionApis(
    SemaphoreAuthApi auth,
    SemaphoreBackupsApi backups,
    SemaphoreProjectsApi projects,
    SemaphoreAccessKeysApi accessKeys,
    SemaphoreSchedulesApi schedules,
    SemaphoreTasksApi tasks,
    SemaphoreUsersApi users) {}
