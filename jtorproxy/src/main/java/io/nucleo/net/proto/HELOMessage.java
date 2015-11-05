package io.nucleo.net.proto;

import io.nucleo.net.ServiceDescriptor;

import java.util.regex.Pattern;

public class HELOMessage implements Message {

    private static final long serialVersionUID = -4582946298578924930L;
    private final String peer;

    public HELOMessage(ServiceDescriptor descriptor) {
        this(descriptor.getFullAddress());
    }

    private HELOMessage(String peer) {
        this.peer = peer;
    }

    public String getPeer() {
        return peer;
    }

    public String getHostname() {
        return peer.split(Pattern.quote(":"))[0];
    }

    public int getPort() {
        return Integer.parseInt(peer.split(Pattern.quote(":"))[1]);
    }

    public String toString() {
        return "HELO " + peer;
    }
}
