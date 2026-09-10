package io.bookwright.api.coverage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SwaggerApiCatalog {

  private static final Set<String> HTTP_METHODS =
      Set.of("get", "post", "put", "delete", "patch", "head", "options");

  private final YAMLMapper yaml = new YAMLMapper();

  List<DocumentedApiOperation> read(InputStream source) throws IOException {
    JsonNode document = yaml.readTree(source);
    JsonNode paths = document.path("paths");
    if (!paths.isObject()) {
      throw new IllegalArgumentException("Swagger document has no paths object");
    }

    String configuredBasePath = document.path("basePath").asText("");
    String basePath =
        configuredBasePath.isBlank() || "/".equals(configuredBasePath)
            ? ""
            : ApiPaths.normalize(configuredBasePath);
    List<DocumentedApiOperation> operations = new ArrayList<>();
    paths
        .properties()
        .forEach(
            path ->
                path.getValue()
                    .properties()
                    .forEach(
                        candidate -> {
                          String method = candidate.getKey().toLowerCase(Locale.ROOT);
                          if (!HTTP_METHODS.contains(method)) {
                            return;
                          }
                          JsonNode tags = candidate.getValue().path("tags");
                          String component =
                              tags.isArray() && !tags.isEmpty() ? tags.get(0).asText() : "untagged";
                          operations.add(
                              new DocumentedApiOperation(
                                  component, new ApiOperation(method, basePath + path.getKey())));
                        }));

    return operations.stream().distinct().sorted(Comparator.naturalOrder()).toList();
  }
}
