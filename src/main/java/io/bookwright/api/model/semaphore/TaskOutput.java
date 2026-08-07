package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TaskOutput(@JsonProperty("task_id") long taskId, String time, String output) {}
