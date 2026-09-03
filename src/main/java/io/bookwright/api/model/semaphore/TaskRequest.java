package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TaskRequest(
    @JsonProperty("template_id") long templateId,
    @JsonProperty("build_task_id") Long buildTaskId,
    String environment,
    String secret,
    String arguments,
    TaskParameters params,
    String message) {

  public TaskRequest(
      long templateId,
      String environment,
      String secret,
      String arguments,
      TaskParameters params,
      String message) {
    this(templateId, null, environment, secret, arguments, params, message);
  }

  public TaskRequest(long templateId) {
    this(templateId, null, null, null, null, null, null);
  }

  @Override
  public String toString() {
    return "TaskRequest[templateId=%d, buildTaskId=%s, environment=%s, secret=[REDACTED], arguments=%s, params=%s, message=%s]"
        .formatted(templateId, buildTaskId, environment, arguments, params, message);
  }
}
