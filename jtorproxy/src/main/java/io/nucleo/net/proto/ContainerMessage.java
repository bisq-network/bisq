package io.nucleo.net.proto;

import java.io.Serializable;

public class ContainerMessage implements Message {


    private static final long serialVersionUID = 9219884444024922023L;
    private final Serializable payload;

    public ContainerMessage(Serializable payload) {
        this.payload = payload;
    }

    public Serializable getPayload() {
        return payload;
    }
}
