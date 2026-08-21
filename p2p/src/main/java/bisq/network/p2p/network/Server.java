/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.network;

import bisq.common.proto.network.NetworkProtoResolver;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import java.io.IOException;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.Nullable;

class Server implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final MessageListener messageListener;
    private final ConnectionListener connectionListener;
    @Nullable
    private final BanFilter banFilter;

    private final ServerSocket serverSocket;
    private final int localPort;
    private final Set<Connection> connections = new CopyOnWriteArraySet<>();
    private final NetworkProtoResolver networkProtoResolver;
    private final Thread serverThread = new Thread(this);


    public Server(ServerSocket serverSocket,
                  MessageListener messageListener,
                  ConnectionListener connectionListener,
                  NetworkProtoResolver networkProtoResolver,
                  @Nullable BanFilter banFilter) {
        this.networkProtoResolver = networkProtoResolver;
        this.serverSocket = serverSocket;
        this.localPort = serverSocket.getLocalPort();
        this.messageListener = messageListener;
        this.connectionListener = connectionListener;
        this.banFilter = banFilter;
    }

    public void start() {
        serverThread.setName("Server-" + localPort);
        serverThread.start();
    }

    @Override
    public void run() {
        try {
            try {
                while (isServerActive()) {
                    log.debug("Ready to accept new clients on port " + localPort);
                    final Socket socket = serverSocket.accept();

                    if (isServerActive()) {
                        log.debug("Accepted new client on localPort/port " + socket.getLocalPort() + "/" + socket.getPort());
                        InboundConnection connection = new InboundConnection(socket,
                                messageListener,
                                connectionListener,
                                networkProtoResolver,
                                banFilter);

                        log.debug("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                                "Server created new inbound connection:"
                                + "\nlocalPort/port={}/{}"
                                + "\nconnection.uid={}", serverSocket.getLocalPort(), socket.getPort(), connection.getUid()
                                + "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n");

                        if (isServerActive())
                            connections.add(connection);
                        else
                            connection.shutDown(CloseConnectionReason.APP_SHUT_DOWN);
                    }
                }
            } catch (IOException e) {
                if (isServerActive())
                    e.printStackTrace();
            }
        } catch (Throwable t) {
            log.error("Executing task failed. " + t.getMessage());
            t.printStackTrace();
        }
    }

    public void shutDown() {
        log.info("Server shutdown started");
        if (isServerActive()) {
            serverThread.interrupt();
            connections.forEach(connection -> connection.shutDown(CloseConnectionReason.APP_SHUT_DOWN));

            try {
                if (!serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (SocketException e) {
                log.debug("SocketException at shutdown might be expected " + e.getMessage());
            } catch (IOException e) {
                log.debug("Exception at shutdown. " + e.getMessage());
            } finally {
                log.debug("Server shutdown complete");
            }
        } else {
            log.warn("stopped already called ast shutdown");
        }
    }

    private boolean isServerActive() {
        return !serverThread.isInterrupted();
    }
}
