package io.bitsquare.storage;

public class ResourceNotFoundException extends Exception {
    public ResourceNotFoundException(String path) {
        super("Resource not found: path = " + path);
    }
}
