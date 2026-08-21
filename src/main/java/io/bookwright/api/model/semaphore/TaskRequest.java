package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TaskRequest(
    @JsonProperty("template_id") long templateId,
    String environment,
    String secret,
    String arguments,
    AnsibleTaskParameters params,
    String message) {

  public TaskRequest(long templateId) {
    this(templateId, null, null, null, null, null);
  }

  @Override
  public String toString() {
    return "TaskRequest[templateId=%d, environment=%s, secret=[REDACTED], arguments=%s, params=%s, message=%s]"
        .formatted(templateId, environment, arguments, params, message);
  }
}
