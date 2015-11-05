package io.nucleo.net.proto;

import io.nucleo.net.ServiceDescriptor;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class IDMessage implements Message {

    private static final long serialVersionUID = -2214485311644580948L;
    private static SecureRandom rnd;

    static {
        try {
            rnd = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private final String id;
    private final long nonce;

    public IDMessage(ServiceDescriptor descriptor) {
        this(descriptor.getFullAddress(), rnd.nextLong());
    }

    private IDMessage(String id, long nonce) {
        this.id = id;
        this.nonce = nonce;
    }

    public String getPeer() {
        return id;
    }

    public IDMessage reply() {
        return new IDMessage(id, nonce);
    }

    public boolean verify(IDMessage msg) {
        return id.equals(msg.id) && (nonce == msg.nonce);
    }

    public String toString() {
        return "ID " + id;
    }
}
