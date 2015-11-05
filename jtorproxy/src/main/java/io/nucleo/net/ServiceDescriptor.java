package io.nucleo.net;

import java.io.IOException;
import java.net.ServerSocket;

public abstract class ServiceDescriptor {

    protected final String hostname;
    protected final int servicePort;
    protected final ServerSocket serverSocket;

    public ServiceDescriptor(String hostname, int servicePort) throws IOException {
        this.hostname = hostname;
        this.servicePort = servicePort;
        this.serverSocket = new ServerSocket();
    }

    public String getHostname() {
        return hostname;
    }

    public int getServicePort() {
        return servicePort;
    }

    public String getFullAddress() {
        return hostname + ":" + servicePort;
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }

}