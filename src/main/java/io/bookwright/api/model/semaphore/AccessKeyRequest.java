package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AccessKeyRequest(
    String name,
    String type,
    @JsonProperty("project_id") long projectId,
    @JsonProperty("login_password") @JsonInclude(JsonInclude.Include.NON_NULL)
        LoginPasswordRequest loginPassword,
    @JsonInclude(JsonInclude.Include.NON_NULL) SshKeyRequest ssh) {

  public AccessKeyRequest(String name, String type, long projectId) {
    this(name, type, projectId, null, null);
  }

  public AccessKeyRequest(
      String name, String type, long projectId, LoginPasswordRequest loginPassword) {
    this(name, type, projectId, loginPassword, null);
  }

  @Override
  public String toString() {
    return "AccessKeyRequest[name=%s, type=%s, projectId=%d, loginPassword=%s, ssh=%s]"
        .formatted(name, type, projectId, loginPassword, ssh);
  }
}
