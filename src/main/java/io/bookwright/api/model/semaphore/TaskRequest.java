package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TaskRequest(
    @JsonProperty("template_id") long templateId,
    @JsonProperty("build_task_id") Long buildTaskId,
    String environment,
    String secret,
    String arguments,
    TaskParameters params,
    String message,
    @JsonProperty("git_branch") String gitBranch) {

  public TaskRequest(
      long templateId,
      Long buildTaskId,
      String environment,
      String secret,
      String arguments,
      TaskParameters params,
      String message) {
    this(templateId, buildTaskId, environment, secret, arguments, params, message, null);
  }

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

  public TaskRequest(long templateId, String gitBranch) {
    this(templateId, null, null, null, null, null, null, gitBranch);
  }

  @Override
  public String toString() {
    return "TaskRequest[templateId=%d, buildTaskId=%s, environment=%s, secret=[REDACTED], arguments=%s, params=%s, message=%s, gitBranch=%s]"
        .formatted(templateId, buildTaskId, environment, arguments, params, message, gitBranch);
  }
}
