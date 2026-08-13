package io.bookwright.api.semaphore;

import io.bookwright.api.semaphore.accesskeys.SemaphoreAccessKeysApi;
import io.bookwright.api.semaphore.projects.SemaphoreProjectsApi;
import io.bookwright.api.semaphore.tasks.SemaphoreTasksApi;
import io.bookwright.api.semaphore.users.SemaphoreUsersApi;

/** Domain clients that share one isolated authenticated cookie jar. */
public record SemaphoreSessionApis(
    SemaphoreProjectsApi projects,
    SemaphoreAccessKeysApi accessKeys,
    SemaphoreTasksApi tasks,
    SemaphoreUsersApi users) {}
