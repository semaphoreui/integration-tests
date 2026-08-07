package io.bookwright.api.semaphore;

/** Domain clients that share one isolated authenticated cookie jar. */
public record SemaphoreSessionApis(
    SemaphoreProjectsApi projects, SemaphoreAccessKeysApi accessKeys) {}
