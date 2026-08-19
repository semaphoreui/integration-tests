package io.bookwright.steps.ui.semaphore.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreOidcFixtures;
import io.bookwright.ui.SemaphoreOidcLoginPage;
import io.qameta.allure.Step;

public class OidcLoginSteps {

  private final SemaphoreOidcLoginPage page;

  @Inject
  public OidcLoginSteps(SemaphoreOidcLoginPage page) {
    this.page = page;
  }

  @Step("Log in to Semaphore through {fixture.providerName} as {fixture.username}")
  public void login(SemaphoreOidcFixtures fixture) {
    page.open(fixture.returnPath());
    PlaywrightAssertions.assertThat(page.providerButton(fixture.providerName())).isVisible();
    page.login(fixture.providerName(), fixture.email(), fixture.password());
    page.waitForReturnPath(fixture.returnPath());
    assertThat(page.currentUserStatus()).as("OIDC browser session status").isEqualTo(200);
  }
}
