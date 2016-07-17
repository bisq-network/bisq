package io.bitsquare.p2p.network;

import io.bitsquare.app.Log;
import io.bitsquare.p2p.network.connection.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

// Runs in UserThread
class Server implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final MessageListener messageListener;
    private final ConnectionListener connectionListener;

    // accessed from different threads
    private final ServerSocket serverSocket;
    private final Set<Connection> connections = new CopyOnWriteArraySet<>();
    private volatile boolean stopped;


    public Server(ServerSocket serverSocket, MessageListener messageListener, ConnectionListener connectionListener) {
        Log.traceCall();
        this.serverSocket = serverSocket;
        this.messageListener = messageListener;
        this.connectionListener = connectionListener;
    }

    @Override
    public void run() {
        Log.traceCall();
        try {
            // Thread created by NetworkNode
            Thread.currentThread().setName("Server-" + serverSocket.getLocalPort());
            try {
                while (!stopped && !Thread.currentThread().isInterrupted()) {
                    log.info("Ready to accept new clients on port " + serverSocket.getLocalPort());
                    final Socket socket = serverSocket.accept();
                    if (!stopped && !Thread.currentThread().isInterrupted()) {
                        log.info("Accepted new client on localPort/port " + socket.getLocalPort() + "/" + socket.getPort());
                        InboundConnection connection = new InboundConnection(socket, messageListener, connectionListener);

                        log.info("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                                "Server created new inbound connection:"
                                + "\nlocalPort/port=" + serverSocket.getLocalPort()
                                + "/" + socket.getPort()
                                + "\nconnection.uid=" + connection.getUid()
                                + "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n");

                        if (!stopped)
                            connections.add(connection);
                        else
                            connection.shutDown(CloseConnectionReason.APP_SHUT_DOWN);
                    }
                }
            } catch (IOException e) {
                if (!stopped)
                    e.printStackTrace();
            }
        } catch (Throwable t) {
            log.error("Executing task failed. " + t.getMessage());
            t.printStackTrace();
        }
    }

    public void shutDown() {
        Log.traceCall();
        if (!stopped) {
            stopped = true;

            connections.stream().forEach(c -> c.shutDown(CloseConnectionReason.APP_SHUT_DOWN));

            try {
                serverSocket.close();
            } catch (SocketException e) {
                log.warn("SocketException at shutdown might be expected " + e.getMessage());
            } catch (IOException e) {
                log.error("Exception at shutdown. " + e.getMessage());
                e.printStackTrace();
            } finally {
                log.info("Server shutdown complete");
            }
        } else {
            log.warn("stopped already called ast shutdown");
        }
    }
}
