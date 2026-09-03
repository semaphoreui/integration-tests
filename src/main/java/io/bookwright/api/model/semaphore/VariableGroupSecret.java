package io.bookwright.api.model.semaphore;

public record VariableGroupSecret(long id, String type, String name, String secret) {

  @Override
  public String toString() {
    return "VariableGroupSecret[id=%d, type=%s, name=%s, secret=[REDACTED]]"
        .formatted(id, type, name);
  }
}
