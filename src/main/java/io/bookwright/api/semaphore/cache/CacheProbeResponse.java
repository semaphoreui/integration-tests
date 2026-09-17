package io.bookwright.api.semaphore.cache;

/** Minimal response evidence retained by a cache-safety probe. */
public record CacheProbeResponse(
    int status, String body, String cacheStatus, String cacheControl) {}
