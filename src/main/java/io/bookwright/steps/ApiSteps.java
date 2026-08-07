package io.bookwright.steps;

import com.google.inject.Inject;
import io.bookwright.steps.local.LocalApiSteps;
import io.bookwright.steps.restfulbooker.RestfulBookerSteps;
import io.bookwright.steps.semaphore.SemaphoreSteps;
import lombok.Getter;
import lombok.experimental.Accessors;

/** The API-test entry point. It exposes targets; each target exposes its focused domains. */
@Getter
@Accessors(fluent = true)
public class ApiSteps {

  @Inject private RestfulBookerSteps restfulBooker;
  @Inject private LocalApiSteps local;
  @Inject private SemaphoreSteps semaphore;
}
