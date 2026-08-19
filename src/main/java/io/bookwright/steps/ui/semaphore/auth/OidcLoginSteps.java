package io.bookwright.steps.ui.semaphore.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreOidcFixtures.Login;
import io.bookwright.fixtures.semaphore.SemaphoreOidcFixtures.Provider;
import io.bookwright.ui.SemaphoreOidcLoginPage;
import io.qameta.allure.Step;

public class OidcLoginSteps {

  private final SemaphoreOidcLoginPage page;

  @Inject
  public OidcLoginSteps(SemaphoreOidcLoginPage page) {
    this.page = page;
  }

  @Step("Log in to Semaphore through OIDC")
  public void login(Login login) {
    page.open(login.returnPath());
    PlaywrightAssertions.assertThat(page.providerButton(login.providerName())).isVisible();
    page.login(login.providerName(), login.account().email(), login.account().password());
    page.waitForReturnPath(login.returnPath());
    assertThat(page.currentUserStatus()).as("OIDC browser session status").isEqualTo(200);
  }

  @Step("Log out from the Semaphore OIDC session")
  public void logout() {
    assertThat(page.logoutStatus()).as("OIDC logout status").isEqualTo(204);
    assertThat(page.currentUserStatus()).as("status after OIDC logout").isEqualTo(401);
  }

  @Step("Verify OIDC login is rejected")
  public void loginAndExpectRejected(Login login) {
    page.open(login.returnPath());
    PlaywrightAssertions.assertThat(page.providerButton(login.providerName())).isVisible();
    page.login(login.providerName(), login.account().email(), login.account().password());
    page.waitForLoginPage();
    assertThat(page.currentUserStatus()).as("status after rejected OIDC login").isEqualTo(401);
  }

  @Step("Verify unavailable OIDC provider is rejected")
  public void unavailableProviderIsRejected(Provider provider) {
    page.open(provider.returnPath());
    PlaywrightAssertions.assertThat(page.providerButton(provider.displayName())).isVisible();
    page.providerButton(provider.displayName()).click();
    page.waitForLoginPage();
    assertThat(page.currentUserStatus()).as("status after OIDC provider failure").isEqualTo(401);
  }
}
