package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TotpRecoveryRequest(@JsonProperty("recovery_code") String recoveryCode) {

  @Override
  public String toString() {
    return "TotpRecoveryRequest[recoveryCode=[REDACTED]]";
  }
}
