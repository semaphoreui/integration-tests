package io.bookwright.api.coverage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.bookwright.config.Configs;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class ApiCoverageReporter {

  private static final String OBSERVATIONS_PROPERTY = "api.coverage.observations";
  private static final String REPORT_PROPERTY = "api.coverage.report";
  private static final String SPECIFICATION_PROPERTY = "api.coverage.spec";

  private ApiCoverageReporter() {}

  public static void main(String[] args) throws IOException {
    Path observationsPath = requiredPath(OBSERVATIONS_PROPERTY);
    if (!Files.isRegularFile(observationsPath)) {
      throw new IllegalStateException(
          "No API coverage observations at %s. Run semaphoreApiCoverage first."
              .formatted(observationsPath));
    }

    List<ApiCoverageObservation> observations =
        Files.readAllLines(observationsPath).stream()
            .filter(line -> !line.isBlank())
            .map(ApiCoverageObservation::parse)
            .toList();
    String specification = specificationLocation();
    List<DocumentedApiOperation> catalog = loadCatalog(specification);
    ApiCoverageAnalyzer.Result result = new ApiCoverageAnalyzer().analyze(catalog, observations);

    writeJson(result, requiredPath(REPORT_PROPERTY));
    print(result, specification, observationsPath);
  }

  private static List<DocumentedApiOperation> loadCatalog(String location) throws IOException {
    if (location.startsWith("http://") || location.startsWith("https://")) {
      Request request = new Request.Builder().url(location).build();
      try (Response response = new OkHttpClient().newCall(request).execute()) {
        if (!response.isSuccessful() || response.body() == null) {
          throw new IllegalStateException(
              "Could not load Swagger API catalog from %s: HTTP %d"
                  .formatted(location, response.code()));
        }
        try (InputStream body = response.body().byteStream()) {
          return new SwaggerApiCatalog().read(body);
        }
      }
    }
    try (InputStream file = Files.newInputStream(Path.of(location))) {
      return new SwaggerApiCatalog().read(file);
    }
  }

  private static String specificationLocation() {
    String configured = System.getProperty(SPECIFICATION_PROPERTY);
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    return Configs.main().uiBaseUrl().replaceAll("/+$", "") + "/swagger/api-docs.yml";
  }

  private static Path requiredPath(String property) {
    String configured = System.getProperty(property);
    if (configured == null || configured.isBlank()) {
      throw new IllegalStateException("Missing required system property: " + property);
    }
    return Path.of(configured);
  }

  private static void writeJson(ApiCoverageAnalyzer.Result result, Path report) throws IOException {
    Files.createDirectories(report.toAbsolutePath().getParent());
    new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(report.toFile(), result);
  }

  private static void print(
      ApiCoverageAnalyzer.Result result, String specification, Path observationsPath) {
    System.out.printf("%nSemaphore documented API coverage%n%n");
    System.out.printf("Specification: %s%n", specification);
    System.out.printf("Observations:  %s%n%n", observationsPath);
    System.out.println(
        "Definition: touched = request observed; successful = at least one response below 400.");
    System.out.println("This measures exercised routes, not assertion quality.\n");
    System.out.printf("Documented operations: %d%n", result.documented());
    System.out.printf(
        Locale.ROOT,
        "Touched operations:    %d (%.1f%%)%n",
        result.touched(),
        result.touchedPercent());
    System.out.printf(
        Locale.ROOT,
        "Successful operations: %d (%.1f%%)%n",
        result.successful(),
        result.successfulPercent());
    System.out.printf("Negative-only:         %d%n%n", result.negativeOnly().size());

    System.out.println("Coverage by component:");
    result.components().forEach(ApiCoverageReporter::printComponent);
    printDocumented("Uncovered documented operations", result.uncovered());
    printDocumented("Documented operations observed only with errors", result.negativeOnly());
    printOperations("Observed but undocumented operations", result.undocumented());
  }

  private static void printComponent(ApiCoverageAnalyzer.ComponentCoverage component) {
    System.out.printf(
        Locale.ROOT,
        "  %-18s touched %3d/%-3d %6.1f%%  successful %3d/%-3d %6.1f%%%n",
        component.component(),
        component.touched(),
        component.documented(),
        component.touchedPercent(),
        component.successful(),
        component.documented(),
        component.successfulPercent());
  }

  private static void printDocumented(String title, List<DocumentedApiOperation> operations) {
    System.out.printf("%n%s (%d):%n", title, operations.size());
    operations.forEach(
        documented ->
            System.out.printf("  [%-16s] %s%n", documented.component(), documented.operation()));
  }

  private static void printOperations(String title, List<ApiOperation> operations) {
    System.out.printf("%n%s (%d):%n", title, operations.size());
    operations.forEach(operation -> System.out.println("  " + operation));
  }
}
