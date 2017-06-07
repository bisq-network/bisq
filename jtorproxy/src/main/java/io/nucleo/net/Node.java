package io.nucleo.net;

import io.nucleo.net.proto.ControlMessage;
import io.nucleo.net.proto.HELOMessage;
import io.nucleo.net.proto.IDMessage;
import io.nucleo.net.proto.Message;
import io.nucleo.net.proto.exceptions.ConnectionException;
import io.nucleo.net.proto.exceptions.ProtocolViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class Node {

    /**
     * Use this whenever to flush the socket header over the socket!
     *
     * @param socket the socket to construct an objectOutputStream from
     * @return the outputstream from the socket
     * @throws IOException in case something goes wrong, duh!
     */
    static ObjectOutputStream prepareOOSForSocket(Socket socket) throws IOException {
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

        out.flush();
        return out;
    }

    private static final Logger log = LoggerFactory.getLogger(Node.class);

    private final ServiceDescriptor descriptor;

    private final HashMap<String, Connection> connections;

    @SuppressWarnings("rawtypes")
    private final TorNode tor;

    private final AtomicBoolean serverRunning;

    public Node(TCPServiceDescriptor descriptor) {
        this(null, descriptor);
    }

    public Node(HiddenServiceDescriptor descriptor, TorNode<?, ?> tor) {
        this(tor, descriptor);
    }

    private Node(TorNode<?, ?> tor, ServiceDescriptor descriptor) {
        this.connections = new HashMap<>();
        this.descriptor = descriptor;
        this.tor = tor;
        this.serverRunning = new AtomicBoolean(false);
    }

    public String getLocalName() {
        return descriptor.getFullAddress();
    }

    public Connection connect(String peer, Collection<ConnectionListener> listeners)
            throws NumberFormatException, IOException {
        if (!serverRunning.get()) {
            throw new IOException("This node has not been started yet!");
        }
        if (peer.equals(descriptor.getFullAddress()))
            throw new IOException("If you find yourself talking to yourself too often, you should really seek help!");
        synchronized (connections) {
            if (connections.containsKey(peer))
                throw new IOException("Already connected to " + peer);
        }

        final Socket sock = connectToService(peer);
        return new OutgoingConnection(peer, sock, listeners);
    }

    private Socket connectToService(String hostname, int port) throws IOException, UnknownHostException, SocketException {
        final Socket sock;
        if (tor != null)
            sock = tor.connectToHiddenService(hostname, port);
        else
            sock = new Socket(hostname, port);
        sock.setSoTimeout(60000);
        return sock;
    }

    private Socket connectToService(String peer) throws IOException, UnknownHostException, SocketException {
        final String[] split = peer.split(Pattern.quote(":"));
        return connectToService(split[0], Integer.parseInt(split[1]));

    }

    public synchronized Server startListening(ServerConnectListener listener) throws IOException {
        if (serverRunning.getAndSet(true))
            throw new IOException("This node is already listening!");
        final Server server = new Server(descriptor.getServerSocket(), listener);
        server.start();
        return server;
    }

    public Connection getConnection(String peerAddress) {
        synchronized (connections) {
            return connections.get(peerAddress);
        }
    }

    public Set<Connection> getConnections() {
        synchronized (connections) {
            return new HashSet<Connection>(connections.values());
        }
    }

    public class Server extends Thread {

        private boolean running;

        private final ServerSocket serverSocket;
        private final ExecutorService executorService;

        private final ServerConnectListener serverConnectListener;

        private Server(ServerSocket serverSocket, ServerConnectListener listener) {
            super("Server");
            this.serverSocket = descriptor.getServerSocket();
            this.serverConnectListener = listener;
            running = true;
            executorService = Executors.newCachedThreadPool();
        }

        public void shutdown() throws IOException {
            running = false;
            synchronized (connections) {
                final Set<Connection> conns = new HashSet<Connection>(connections.values());
                for (Connection con : conns) {
                    con.close();
                }
            }
            serverSocket.close();
            try {
                executorService.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Node.this.serverRunning.set(false);
            log.debug("Server successfully shutdown");
        }

        @Override
        public void run() {
            try {
                while (running) {
                    final Socket socket = serverSocket.accept();
                    log.debug("Accepting Client on port " + socket.getLocalPort());
                    executorService.submit(new Acceptor(socket));
                }
            } catch (IOException e) {
                if (running)
                    e.printStackTrace();
            }
        }

        private boolean verifyIdentity(HELOMessage helo, ObjectInputStream in) throws IOException {
            log.debug("Verifying HELO msg");
            final Socket sock = connectToService(helo.getHostname(), helo.getPort());

            log.debug("Connected to advertised client " + helo.getPeer());
            ObjectOutputStream out = prepareOOSForSocket(sock);
            final IDMessage challenge = new IDMessage(descriptor);
            out.writeObject(challenge);
            log.debug("Sent IDMessage to");
            out.flush();
            // wait for other side to close
            try {
                while (sock.getInputStream().read() != -1)
                    ;
            } catch (IOException e) {
                // no matter
            }
            out.close();
            sock.close();
            log.debug("Closed socket after sending IDMessage");
            try {
                log.debug("Waiting for response of challenge");
                IDMessage response = (IDMessage) in.readObject();
                log.debug("Got response for challenge");
                final boolean verified = challenge.verify(response);
                log.debug("Response verified correctly!");
                return verified;
            } catch (ClassNotFoundException e) {
                new ProtocolViolationException(e).printStackTrace();
            }
            return false;
        }

        private class Acceptor implements Runnable {

            private final Socket socket;

            private Acceptor(Socket socket) {
                this.socket = socket;
            }

            @Override
            public void run() {
                {
                    try {
                        socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(60));
                    } catch (SocketException e2) {
                        e2.printStackTrace();
                        try {
                            socket.close();
                        } catch (IOException e) {
                        }
                        return;
                    }

                    ObjectInputStream objectInputStream = null;
                    ObjectOutputStream out = null;

                    // get incoming data
                    try {
                        out = prepareOOSForSocket(socket);
                        // LookAheadObjectInputStream not needed here as the class it not used in bisq (used to test the library)
                        objectInputStream = new ObjectInputStream(socket.getInputStream());
                    } catch (EOFException e) {
                        log.debug("Got bogus incoming connection");
                    } catch (IOException e) {
                        e.printStackTrace();
                        try {
                            socket.close();
                        } catch (IOException e1) {
                        }
                        return;
                    }

                    String peer = null;
                    try {
                        log.debug("Waiting for HELO or Identification");
                        final Message helo = (Message) objectInputStream.readObject();
                        if (helo instanceof HELOMessage) {
                            peer = ((HELOMessage) helo).getPeer();
                            log.debug("Got HELO from " + peer);
                            boolean alreadyConnected;
                            synchronized (connections) {
                                alreadyConnected = connections.containsKey(peer);
                            }
                            if (alreadyConnected || !verifyIdentity((HELOMessage) helo, objectInputStream)) {
                                log.debug(alreadyConnected ? ("already connected to " + peer) : "verification failed");
                                out.writeObject(alreadyConnected ? ControlMessage.ALREADY_CONNECTED : ControlMessage.HANDSHAKE_FAILED);
                                out.writeObject(ControlMessage.DISCONNECT);
                                out.flush();
                                out.close();
                                objectInputStream.close();
                                socket.close();
                                return;
                            }
                            log.debug("Verification of " + peer + " successful");
                        } else if (helo instanceof IDMessage) {
                            peer = ((IDMessage) helo).getPeer();
                            log.debug("got IDMessage from " + peer);
                            final Connection client = connections.get(peer);
                            if (client != null) {
                                log.debug("Got preexisting connection for " + peer);
                                client.sendMsg(((IDMessage) helo).reply());
                                log.debug("Sent response for challenge");
                            } else {
                                log.debug("Got IDMessage for unknown connection to " + peer);
                            }
                            out.flush();
                            out.close();
                            objectInputStream.close();
                            socket.close();
                            log.debug("Closed socket for identification");
                            return;

                        } else
                            throw new ClassNotFoundException("First Message was neither HELO, nor ID");
                    } catch (ClassNotFoundException e) {
                        new ProtocolViolationException(e);
                    } catch (IOException e) {
                        try {
                            objectInputStream.close();
                            out.close();
                            socket.close();
                        } catch (IOException e1) {
                        }
                        return;
                    }
                    // Here we go
                    log.debug("Incoming Connection ready!");
                    try {
                        // TODO: listeners are only added afterwards, so network_messages can be lost!
                        IncomingConnection incomingConnection = new IncomingConnection(peer, socket, out, objectInputStream);
                        serverConnectListener.onConnect(incomingConnection);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private class IncomingConnection extends Connection {
        private IncomingConnection(String peer, Socket socket, ObjectOutputStream out, ObjectInputStream in)
                throws IOException {
            super(peer, socket, out, in);
            synchronized (connections) {
                connections.put(peer, this);
            }
            sendMsg(ControlMessage.AVAILABLE);
        }

        @Override
        public void listen() throws ConnectionException {
            super.listen();
            onReady();
        }

        @Override
        protected void onMessage(Message msg) throws IOException {
            if ((msg instanceof ControlMessage) && (ControlMessage.HEARTBEAT == msg)) {
                log.debug("RX+REPLY HEARTBEAT");
                try {
                    sendMsg(ControlMessage.HEARTBEAT);
                } catch (IOException e) {
                    onError(e);
                }
            } else
                super.onMessage(msg);
        }

        @Override
        public void onDisconnect() {
            synchronized (connections) {
                connections.remove(getPeer());
            }
        }

        @Override
        public boolean isIncoming() {
            return true;
        }
    }

    private class OutgoingConnection extends Connection {

        private OutgoingConnection(String peer, Socket socket, Collection<ConnectionListener> listeners)
                throws IOException {
            super(peer, socket);
            synchronized (connections) {
                connections.put(peer, this);
            }
            setConnectionListeners(listeners);
            try {
                listen();
            } catch (ConnectionException e) {
                // Never happens
            }
            log.debug("Sending HELO");
            sendMsg(new HELOMessage(descriptor));
            log.debug("Sent HELO");
        }

        @Override
        public void onDisconnect() {
            synchronized (connections) {
                connections.remove(getPeer());
            }
        }

        @Override
        public boolean isIncoming() {
            return false;
        }

    }
}
