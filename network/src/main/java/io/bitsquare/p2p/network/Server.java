package io.bitsquare.p2p.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final ServerSocket serverSocket;
    private final MessageListener messageListener;
    private final ConnectionListener connectionListener;
    private final List<Connection> connections = new CopyOnWriteArrayList<>();
    private volatile boolean stopped;


    public Server(ServerSocket serverSocket, MessageListener messageListener, ConnectionListener connectionListener) {
        this.serverSocket = serverSocket;
        this.messageListener = messageListener;
        this.connectionListener = connectionListener;
    }

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("Server-" + serverSocket.getLocalPort());
            while (!stopped) {
                try {
                    log.info("Ready to accept new clients on port " + serverSocket.getLocalPort());
                    final Socket socket = serverSocket.accept();
                    log.info("Accepted new client on port " + socket.getLocalPort());
                    Connection connection = new Connection(socket, messageListener, connectionListener);
                    log.info("\n\nServer created new inbound connection:"
                            + "\nserverSocket.getLocalPort()=" + serverSocket.getLocalPort()
                            + "\nsocket.getPort()=" + socket.getPort()
                            + "\nconnection.uid=" + connection.getUid()
                            + "\n\n");

                    log.info("Server created new socket with port " + socket.getPort());
                    connections.add(connection);
                } catch (IOException e) {
                    if (!stopped)
                        e.printStackTrace();
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            log.error("Executing task failed. " + t.getMessage());
        }
    }

    public void shutDown() {
        if (!stopped) {
            stopped = true;

            connections.stream().forEach(e -> e.shutDown());

            try {
                serverSocket.close();
            } catch (SocketException e) {
                log.warn("SocketException at shutdown might be expected " + e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                log.debug("Server shutdown complete");
            }
        }
    }
}
