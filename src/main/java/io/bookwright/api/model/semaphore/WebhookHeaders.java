package io.bookwright.api.model.semaphore;

import java.util.Map;

/** Webhook headers whose values must never be rendered in test diagnostics. */
public record WebhookHeaders(Map<String, String> values) {

  public WebhookHeaders {
    values = Map.copyOf(values);
  }

  @Override
  public String toString() {
    return "WebhookHeaders[values=[REDACTED]]";
  }
}
