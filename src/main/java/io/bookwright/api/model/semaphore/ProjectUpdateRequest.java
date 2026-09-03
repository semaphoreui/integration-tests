package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProjectUpdateRequest(
    long id,
    String name,
    boolean alert,
    @JsonProperty("max_parallel_tasks") int maxParallelTasks) {}
