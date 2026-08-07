package io.bookwright.api.semaphore;

import io.bookwright.api.semaphore.accesskeys.SemaphoreAccessKeysApi;
import io.bookwright.api.semaphore.projects.SemaphoreProjectsApi;

/** Domain clients that share one isolated authenticated cookie jar. */
public record SemaphoreSessionApis(
    SemaphoreProjectsApi projects, SemaphoreAccessKeysApi accessKeys) {}
