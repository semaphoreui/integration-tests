package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProjectRequest(
    String name, boolean alert, @JsonProperty("max_parallel_tasks") int maxParallelTasks) {}
