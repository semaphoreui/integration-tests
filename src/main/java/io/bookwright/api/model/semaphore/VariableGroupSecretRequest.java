package io.bookwright.api.model.semaphore;

public record VariableGroupSecretRequest(
    long id, String type, String name, String secret, String operation) {

  @Override
  public String toString() {
    return "VariableGroupSecretRequest[id=%d, type=%s, name=%s, secret=[REDACTED], operation=%s]"
        .formatted(id, type, name, operation);
  }
}
