package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Create or update payload of a credential mapping; {@code id} is only sent on update. */
public record HostConfigRequest(
    @JsonInclude(JsonInclude.Include.NON_NULL) Long id,
    @JsonProperty("project_id") long projectId,
    String type,
    String host,
    @JsonProperty("ssh_key_id") long sshKeyId) {

  public HostConfigRequest(long projectId, String type, String host, long sshKeyId) {
    this(null, projectId, type, host, sshKeyId);
  }

  public HostConfigRequest withId(long hostConfigId) {
    return new HostConfigRequest(hostConfigId, projectId, type, host, sshKeyId);
  }
}
