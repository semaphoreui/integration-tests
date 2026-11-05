package io.bookwright.tests.framework;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import io.bookwright.ui.BrowserManager;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class BrowserDiagnosticsRedactionTest {

  @Test
  void browserEventsAndCurrentPageDoNotExposeAuthenticationUrls() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.start();
      server.enqueue(
          new MockResponse()
              .addHeader("Content-Type", "text/html")
              .setBody(
                  "<html><head><link rel='icon' href='data:,'></head><body>Diagnostics fixture</body></html>"));
      Page page = BrowserManager.page();
      AutoCloseable session = BrowserManager.sessionResource();
      try {
        page.navigate(
            server.url("/callback?code=current-page-code&state=current-page-state")
                + "#current-page-fragment");
        page.evaluate(
            "url => console.error('Callback failed at ' + url)",
            server.url("/callback?code=console-code&state=console-state").toString());
        page.evaluate(
            "url => setTimeout(() => { throw new Error('Callback failed at ' + url); }, 0)",
            server.url("/callback?code=exception-code").toString());
        page.route("**/blocked**", route -> route.abort());
        page.evaluate(
            "url => fetch(url).catch(() => {})",
            server.url("/blocked?code=request-code&state=request-state").toString());
        page.waitForCondition(
            () ->
                BrowserManager.diagnosticsReport().contains("Page errors (1)")
                    && BrowserManager.diagnosticsReport().contains("Failed requests (1)"));

        assertThat(BrowserManager.diagnosticsReport())
            .contains(
                "Callback failed at",
                "Page errors (1)",
                "Failed requests (1)",
                "%5BREDACTED%5D",
                "/callback",
                "/blocked")
            .doesNotContain(
                "current-page-code",
                "current-page-state",
                "current-page-fragment",
                "console-code",
                "console-state",
                "exception-code",
                "request-code",
                "request-state");
      } finally {
        BrowserManager.closeContext();
        session.close();
      }
    }
  }
}
