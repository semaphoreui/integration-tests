package io.bookwright.api.coverage;

final class ApiPaths {

  private ApiPaths() {}

  static String apiPath(String encodedPath) {
    int apiStart = encodedPath.indexOf("/api/");
    if (apiStart >= 0) {
      return normalize(encodedPath.substring(apiStart));
    }
    return encodedPath.endsWith("/api") ? "/api" : null;
  }

  static String normalize(String path) {
    String normalized = path.startsWith("/") ? path : "/" + path;
    while (normalized.length() > 1 && normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  static String anonymizeUnknown(String path) {
    return path.replaceAll("/(?i:[0-9a-f]{8}-[0-9a-f-]{27,})", "/{id}")
        .replaceAll(
            "/(?=[A-Za-z0-9_-]{12,}(?:/|$))(?=[^/]*[A-Za-z])(?=[^/]*\\d)[A-Za-z0-9_-]+", "/{id}")
        .replaceAll("/\\d+", "/{id}");
  }
}
