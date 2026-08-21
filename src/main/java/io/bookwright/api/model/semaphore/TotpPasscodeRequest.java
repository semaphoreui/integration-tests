package io.bookwright.api.model.semaphore;

public record TotpPasscodeRequest(String passcode) {

  @Override
  public String toString() {
    return "TotpPasscodeRequest[passcode=[REDACTED]]";
  }
}
