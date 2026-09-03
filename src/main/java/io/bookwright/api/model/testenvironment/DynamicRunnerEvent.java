package io.bookwright.api.model.testenvironment;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DynamicRunnerEvent(
    String type,
    @JsonProperty("task_id") long taskId,
    @JsonProperty("runner_id") long runnerId,
    @JsonProperty("exit_code") Integer exitCode) {}
