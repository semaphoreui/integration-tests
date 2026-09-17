package io.bookwright.api.semaphore.cache;

/** Isolated cookie session for one cache-boundary user. */
public record WebCacheSession(SemaphoreWebCacheApi api) {}
