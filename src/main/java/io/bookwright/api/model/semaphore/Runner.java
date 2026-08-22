package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record Runner(
    long id,
    @JsonProperty("project_id") Long projectId,
    String name,
    String webhook,
    boolean active,
    @JsonProperty("is_default") boolean defaultRunner,
    boolean registered,
    String status,
    Instant touched,
    @JsonProperty("max_parallel_tasks") int maxParallelTasks,
    List<String> tags) {}
