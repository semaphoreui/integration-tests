package io.bookwright.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/** Per-client cookie storage used for APIs, such as Semaphore, that authenticate by session. */
public class InMemoryCookieJar implements CookieJar {

  private final Map<String, Cookie> cookies = new ConcurrentHashMap<>();

  @Override
  public void saveFromResponse(HttpUrl url, List<Cookie> responseCookies) {
    responseCookies.forEach(cookie -> cookies.put(cookie.name(), cookie));
  }

  @Override
  public List<Cookie> loadForRequest(HttpUrl url) {
    long now = System.currentTimeMillis();
    cookies.values().removeIf(cookie -> cookie.expiresAt() < now);
    return cookies.values().stream().filter(cookie -> cookie.matches(url)).toList();
  }
}
