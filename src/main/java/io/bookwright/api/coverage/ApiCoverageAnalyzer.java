package io.bookwright.api.coverage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class ApiCoverageAnalyzer {

  Result analyze(List<DocumentedApiOperation> catalog, List<ApiCoverageObservation> observations) {
    Map<DocumentedApiOperation, List<ApiCoverageObservation>> matched = new HashMap<>();
    Set<ApiOperation> undocumented = new TreeSet<>();

    for (ApiCoverageObservation observation : observations) {
      DocumentedApiOperation documented = match(catalog, observation.operation());
      if (documented == null) {
        undocumented.add(
            new ApiOperation(
                observation.operation().method(),
                ApiPaths.anonymizeUnknown(observation.operation().path())));
      } else {
        matched.computeIfAbsent(documented, ignored -> new ArrayList<>()).add(observation);
      }
    }

    Set<DocumentedApiOperation> touched = matched.keySet();
    Set<DocumentedApiOperation> successful = new HashSet<>();
    matched.forEach(
        (operation, hits) -> {
          if (hits.stream().anyMatch(ApiCoverageObservation::successful)) {
            successful.add(operation);
          }
        });

    List<DocumentedApiOperation> uncovered =
        catalog.stream().filter(operation -> !touched.contains(operation)).sorted().toList();
    List<DocumentedApiOperation> negativeOnly =
        touched.stream().filter(operation -> !successful.contains(operation)).sorted().toList();

    List<ComponentCoverage> components =
        catalog.stream()
            .map(DocumentedApiOperation::component)
            .distinct()
            .sorted()
            .map(
                component -> {
                  List<DocumentedApiOperation> componentOperations =
                      catalog.stream()
                          .filter(operation -> operation.component().equals(component))
                          .toList();
                  long componentTouched =
                      componentOperations.stream().filter(touched::contains).count();
                  long componentSuccessful =
                      componentOperations.stream().filter(successful::contains).count();
                  return new ComponentCoverage(
                      component,
                      componentOperations.size(),
                      (int) componentTouched,
                      (int) componentSuccessful);
                })
            .toList();

    return new Result(
        catalog.size(),
        touched.size(),
        successful.size(),
        List.copyOf(components),
        uncovered,
        negativeOnly,
        List.copyOf(undocumented));
  }

  private DocumentedApiOperation match(
      List<DocumentedApiOperation> catalog, ApiOperation observed) {
    return catalog.stream()
        .filter(documented -> documented.operation().method().equals(observed.method()))
        .filter(documented -> pathMatches(documented.operation().path(), observed.path()))
        .max(Comparator.comparingInt(DocumentedApiOperation::literalSegments))
        .orElse(null);
  }

  private boolean pathMatches(String documented, String observed) {
    String[] documentedSegments = documented.split("/");
    String[] observedSegments = observed.split("/");
    if (documentedSegments.length != observedSegments.length) {
      return false;
    }
    for (int index = 0; index < documentedSegments.length; index++) {
      String expected = documentedSegments[index];
      if (!expected.startsWith("{") && !expected.equals(observedSegments[index])) {
        return false;
      }
    }
    return true;
  }

  record ComponentCoverage(String component, int documented, int touched, int successful) {

    double touchedPercent() {
      return percent(touched, documented);
    }

    double successfulPercent() {
      return percent(successful, documented);
    }
  }

  record Result(
      int documented,
      int touched,
      int successful,
      List<ComponentCoverage> components,
      List<DocumentedApiOperation> uncovered,
      List<DocumentedApiOperation> negativeOnly,
      List<ApiOperation> undocumented) {

    double touchedPercent() {
      return percent(touched, documented);
    }

    double successfulPercent() {
      return percent(successful, documented);
    }
  }

  private static double percent(int covered, int total) {
    return total == 0 ? 0 : covered * 100.0 / total;
  }
}
