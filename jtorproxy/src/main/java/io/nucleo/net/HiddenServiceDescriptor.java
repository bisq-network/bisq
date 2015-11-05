package io.nucleo.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class HiddenServiceDescriptor extends ServiceDescriptor {

    private final int localPort;

    public HiddenServiceDescriptor(String serviceName, int localPort, int servicePort) throws IOException {
        super(serviceName, servicePort);
        this.localPort = localPort;
        this.serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), localPort));
    }

    public int getLocalPort() {
        return localPort;
    }
}
