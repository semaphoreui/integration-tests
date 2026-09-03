package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SshKeyRequest(
    String login, String passphrase, @JsonProperty("private_key") String privateKey) {

  @Override
  public String toString() {
    return "SshKeyRequest[login=%s, passphrase=[REDACTED], privateKey=[REDACTED]]".formatted(login);
  }
}
