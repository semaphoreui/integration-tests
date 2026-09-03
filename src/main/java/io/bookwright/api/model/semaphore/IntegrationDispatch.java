package io.bookwright.api.model.semaphore;

public record IntegrationDispatch(
    long taskId, long templateId, long projectId, long integrationId, Long inventoryId) {}
