package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A credential mapping: an ssh host or a git URL prefix bound to a project access key. */
public record HostConfig(
    long id,
    @JsonProperty("project_id") long projectId,
    String type,
    String host,
    @JsonProperty("ssh_key_id") long sshKeyId) {}
