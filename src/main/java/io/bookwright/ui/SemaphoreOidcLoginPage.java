package io.bookwright.ui;

import com.google.inject.Inject;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.Cookie;
import io.bookwright.config.MainConfig;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class SemaphoreOidcLoginPage {

  private static final String SESSION_COOKIE = "semaphore";

  private final Page page;
  private final MainConfig config;

  @Inject
  public SemaphoreOidcLoginPage(Page page, MainConfig config) {
    this.page = page;
    this.config = config;
  }

  public void open(String returnPath) {
    page.navigate(
        config.uiBaseUrl()
            + "/auth/login?return="
            + URLEncoder.encode(returnPath, StandardCharsets.UTF_8));
  }

  public Locator providerButton(String providerName) {
    return page.getByRole(
        AriaRole.BUTTON, new Page.GetByRoleOptions().setName(providerName).setExact(true));
  }

  public void login(String providerName, String email, String password) {
    providerButton(providerName).click();
    page.locator("input[name=login]").fill(email);
    page.locator("input[name=password]").fill(password);
    page.locator("button[type=submit]").click();
  }

  public void waitForReturnPath(String returnPath) {
    page.waitForURL(config.uiBaseUrl() + returnPath);
  }

  public void waitForLoginPage() {
    page.waitForURL(config.uiBaseUrl() + "/auth/login");
  }

  public int currentUserStatus() {
    return status(page.context().request().get(config.uiBaseUrl() + "/api/user"));
  }

  public int logoutStatus() {
    return status(page.context().request().post(config.uiBaseUrl() + "/api/auth/logout"));
  }

  public Cookie sessionCookie() {
    return page.context().cookies().stream()
        .filter(cookie -> SESSION_COOKIE.equals(cookie.name))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("OIDC login did not create a session cookie"));
  }

  public boolean publicOriginUsesHttps() {
    return "https".equalsIgnoreCase(URI.create(config.uiBaseUrl()).getScheme());
  }

  private int status(APIResponse response) {
    try {
      return response.status();
    } finally {
      response.dispose();
    }
  }
}
