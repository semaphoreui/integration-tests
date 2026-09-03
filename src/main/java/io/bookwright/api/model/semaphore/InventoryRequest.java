package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InventoryRequest(
    String name,
    @JsonProperty("project_id") long projectId,
    String inventory,
    @JsonProperty("ssh_key_id") Long sshKeyId,
    @JsonProperty("become_key_id") Long becomeKeyId,
    @JsonProperty("repository_id") Long repositoryId,
    String type) {

  public InventoryRequest(
      String name, long projectId, String inventory, long sshKeyId, String type) {
    this(name, projectId, inventory, sshKeyId, null, null, type);
  }
}
