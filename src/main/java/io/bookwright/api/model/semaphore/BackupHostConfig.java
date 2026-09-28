package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A credential mapping as it appears in a project backup: the key is carried by name. */
public record BackupHostConfig(String type, String name, @JsonProperty("ssh_key") String sshKey) {}
