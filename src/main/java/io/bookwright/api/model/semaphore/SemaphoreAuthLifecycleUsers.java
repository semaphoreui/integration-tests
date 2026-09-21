package io.bookwright.api.model.semaphore;

/** Runtime users prepared by the authentication lifecycle precondition. */
public record SemaphoreAuthLifecycleUsers(SemaphoreTestUser actor, SemaphoreTestUser target) {}
