package io.bisq.common.storage;

public class ResourceNotFoundException extends Exception {
    public ResourceNotFoundException(String path) {
        super("Resource not found: path = " + path);
    }
}
