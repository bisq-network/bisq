package io.nucleo.net;

import java.io.IOException;
import java.net.InetSocketAddress;

public class TCPServiceDescriptor extends ServiceDescriptor {

    public TCPServiceDescriptor(String hostname, int servicePort) throws IOException {
        super(hostname, servicePort);
        getServerSocket().bind(new InetSocketAddress(hostname, servicePort));
    }

}
