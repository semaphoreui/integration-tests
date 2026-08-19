package io.bookwright.steps.ui.semaphore;

import com.google.inject.Inject;
import io.bookwright.steps.ui.semaphore.auth.OidcLoginSteps;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public class SemaphoreUiSteps {
  @Inject private OidcLoginSteps oidc;
}
