package io.bookwright.api.model.testenvironment;

import java.util.List;

public record DynamicRunnerState(List<DynamicRunnerEvent> events) {}
