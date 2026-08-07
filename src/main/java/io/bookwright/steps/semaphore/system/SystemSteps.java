package io.bookwright.steps.semaphore.system;

import com.google.inject.Inject;
import io.bookwright.api.semaphore.system.SemaphoreSystemApi;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.io.IOException;

public class SystemSteps {

  private final SemaphoreSystemApi api;

  @Inject
  public SystemSteps(SemaphoreSystemApi api) {
    this.api = api;
  }

  @Step("Check Semaphore API health")
  public void health() {
    var response = Calls.expectStatus(api.ping(), 200);
    try (var body = response.body()) {
      if (body == null || !"pong".equals(body.string())) {
        throw new IllegalStateException("Expected health response body 'pong'");
      }
    } catch (IOException e) {
      throw new IllegalStateException("Could not read health response", e);
    }
  }
}
