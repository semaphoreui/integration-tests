package io.bookwright.api.coverage;

import java.util.Locale;

public record ApiOperation(String method, String path) implements Comparable<ApiOperation> {

  public ApiOperation {
    method = method.toUpperCase(Locale.ROOT);
    path = ApiPaths.normalize(path);
  }

  @Override
  public int compareTo(ApiOperation other) {
    int pathComparison = path.compareTo(other.path);
    return pathComparison != 0 ? pathComparison : method.compareTo(other.method);
  }

  @Override
  public String toString() {
    return "%s %s".formatted(method, path);
  }
}
