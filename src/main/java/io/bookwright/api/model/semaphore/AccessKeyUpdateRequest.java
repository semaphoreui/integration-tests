package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AccessKeyUpdateRequest(
    long id,
    String name,
    String type,
    @JsonProperty("project_id") long projectId,
    @JsonProperty("override_secret") boolean overrideSecret,
    @JsonProperty("login_password") @JsonInclude(JsonInclude.Include.NON_NULL)
        LoginPasswordRequest loginPassword,
    @JsonInclude(JsonInclude.Include.NON_NULL) SshKeyRequest ssh) {

  @Override
  public String toString() {
    return "AccessKeyUpdateRequest[id=%d, name=%s, type=%s, projectId=%d, overrideSecret=%s, loginPassword=%s, ssh=%s]"
        .formatted(id, name, type, projectId, overrideSecret, loginPassword, ssh);
  }
}
