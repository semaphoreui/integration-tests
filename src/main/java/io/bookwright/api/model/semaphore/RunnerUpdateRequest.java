package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RunnerUpdateRequest(
    String name,
    boolean active,
    @JsonProperty("is_default") boolean defaultRunner,
    String webhook,
    @JsonProperty("max_parallel_tasks") int maxParallelTasks,
    List<String> tags) {

  public static RunnerUpdateRequest from(Runner runner) {
    return new RunnerUpdateRequest(
        runner.name(),
        runner.active(),
        runner.defaultRunner(),
        runner.webhook(),
        runner.maxParallelTasks(),
        runner.tags());
  }
}
