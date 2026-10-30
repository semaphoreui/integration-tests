package io.bookwright.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UiDiagnosticsTest {

  @Test
  void removesCallbackMaterialFromEveryDiagnosticChannel() {
    UiDiagnostics diagnostics = new UiDiagnostics();
    diagnostics.recordConsoleError(
        "Failed at https://example.test/callback?code=console-code&view=compact",
        "https://example.test/callback?state=location-state:12:5");
    diagnostics.recordPageError(
        "Error at https://example.test/callback?nonce=page-error-nonce\n"
            + "at https://example.test/app.js?access_token=stack-token:10:2");
    diagnostics.recordFailedRequest(
        "GET",
        "https://example.test/callback?code=request-code",
        "document",
        "Failure at https://example.test/callback?state=failure-state");

    assertThat(
            diagnostics.report(
                "https://fixture-user:fixture-password@example.test/semaphore/callback?code=page-code&state=page-state#fragment-secret",
                1920,
                1080))
        .contains("example.test/semaphore/callback", "view=compact", "[document]", "%5BREDACTED%5D")
        .doesNotContain(
            "console-code",
            "location-state",
            "page-error-nonce",
            "stack-token",
            "request-code",
            "failure-state",
            "fixture-user",
            "fixture-password",
            "page-code",
            "page-state",
            "fragment-secret");
  }

  @Test
  void sanitizesMultipleUrlsAndOmitsMalformedOnes() {
    UiDiagnostics diagnostics = new UiDiagnostics();
    diagnostics.recordPageError(
        "HTTP://example.test/callback?code=first-secret and "
            + "https://example.test/callback#second-secret then https://[invalid?state=third-secret");

    assertThat(diagnostics.report("about:blank", 1280, 720))
        .contains("http://example.test/callback", "https://example.test/callback", "[URL OMITTED]")
        .doesNotContain("first-secret", "second-secret", "third-secret");
  }

  @Test
  void redactsBeforeTruncationAndKeepsCategoryBounds() {
    UiDiagnostics diagnostics = new UiDiagnostics();
    String padding = "x".repeat(1_900);
    for (int index = 0; index < 105; index++) {
      diagnostics.recordPageError(
          padding + " https://example.test/callback?state=" + "boundary-secret".repeat(200));
    }

    String report = diagnostics.report("about:blank", 1280, 720);
    assertThat(report)
        .contains("Page errors (100)", "%5BREDACTED%5D")
        .doesNotContain("boundary-secret");
    assertThat(report.lines().filter(line -> line.matches("\\d+\\. .*")))
        .hasSize(100)
        .allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(2_005));
  }

  @Test
  void reportsPageContextAndCollectedFailures() {
    UiDiagnostics diagnostics = new UiDiagnostics();
    diagnostics.recordConsoleError("ReferenceError: app is not defined", "app.js:42");
    diagnostics.recordPageError("Unhandled promise rejection");
    diagnostics.recordFailedRequest(
        "GET",
        "https://example.test/profile?token=browser-secret&view=compact",
        "xhr",
        "net::ERR_FAILED");

    String report =
        diagnostics.report("https://example.test/inventory?access_token=page-secret", 1920, 1080);

    assertThat(report)
        .contains(
            "URL: https://example.test/inventory?access_token=%5BREDACTED%5D",
            "Viewport: 1920x1080",
            "Console errors (1)",
            "ReferenceError: app is not defined at app.js:42",
            "Page errors (1)",
            "Unhandled promise rejection",
            "Failed requests (1)",
            "view=compact",
            "token=%5BREDACTED%5D",
            "net::ERR_FAILED")
        .doesNotContain("browser-secret", "page-secret");
  }

  @Test
  void reportsEmptyCategoriesExplicitly() {
    String report = new UiDiagnostics().report("about:blank", 1280, 720);

    assertThat(report)
        .contains("Viewport: 1280x720")
        .contains("Console errors (0):\n<none>")
        .contains("Page errors (0):\n<none>")
        .contains("Failed requests (0):\n<none>");
  }
}
