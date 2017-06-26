package io.nucleo.net;

import io.nucleo.net.proto.ContainerMessage;
import io.nucleo.net.proto.ControlMessage;
import io.nucleo.net.proto.Message;
import io.nucleo.net.proto.exceptions.ConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Connection implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(Connection.class);

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private final LinkedList<ConnectionListener> connectionListeners;
    private final String peer;
    private boolean running;
    private final AtomicBoolean available;
    private final AtomicBoolean listening;

    private final ExecutorService executorService;
    private final InputStreamListener inputStreamListener;

    private final AtomicBoolean heartBeating;

    public Connection(String peer, Socket socket) throws IOException {
        // LookAheadObjectInputStream not needed here as the class it not used in bisq (used to test the library)
        this(peer, socket, Node.prepareOOSForSocket(socket), new ObjectInputStream(socket.getInputStream()));
    }

    Connection(String peer, Socket socket, ObjectOutputStream out, ObjectInputStream in) {
        log.debug("Initiating new connection");
        this.available = new AtomicBoolean(false);
        this.peer = peer;
        this.socket = socket;
        this.in = in;
        this.out = out;
        running = true;
        listening = new AtomicBoolean(false);
        heartBeating = new AtomicBoolean(false);
        this.connectionListeners = new LinkedList<>();
        this.inputStreamListener = new InputStreamListener();
        executorService = Executors.newCachedThreadPool();
    }

    public abstract boolean isIncoming();

    public void addMessageListener(ConnectionListener listener) {
        synchronized (connectionListeners) {
            connectionListeners.add(listener);
        }
    }

    protected void setConnectionListeners(Collection<ConnectionListener> listeners) {
        synchronized (listeners) {
            this.connectionListeners.clear();
            this.connectionListeners.addAll(listeners);
        }
    }

    public void removeMessageListener(ConnectionListener listener) {
        synchronized (connectionListeners) {
            connectionListeners.remove(listener);
        }
    }

    void sendMsg(Message msg) throws IOException {
        out.writeObject(msg);
        out.flush();
    }

    public void sendMessage(ContainerMessage msg) throws IOException {
        if (!available.get())
            throw new IOException("Connection is not yet available!");
        sendMsg(msg);
    }

    protected void onMessage(Message msg) throws IOException {
        log.debug("RXD: " + msg.toString());
        if (msg instanceof ContainerMessage) {
            synchronized (connectionListeners) {
                for (ConnectionListener l : connectionListeners)
                    l.onMessage(this, (ContainerMessage) msg);
            }
        } else {
            if (msg instanceof ControlMessage) {
                switch ((ControlMessage) msg) {
                    case DISCONNECT:
                        close(false, PredefinedDisconnectReason.createReason(PredefinedDisconnectReason.CONNECTION_CLOSED, true));
                        break;
                    case AVAILABLE:
                        startHeartbeat();
                        onReady();
                        break;
                    default:
                        break;
                }
            }
        }
    }

    protected void onReady() {
        if (!available.getAndSet(true)) {
            synchronized (connectionListeners) {
                for (ConnectionListener l : connectionListeners) {
                    l.onReady(this);
                }
            }
        }
    }

    protected abstract void onDisconnect();

    private void onDisconn(DisconnectReason reason) {
        onDisconnect();
        synchronized (connectionListeners) {
            for (ConnectionListener l : connectionListeners) {
                l.onDisconnect(this, reason);
            }
        }
    }

    private void onTimeout() {
        try {
            close(false, PredefinedDisconnectReason.TIMEOUT);
        } catch (IOException e1) {
        }
    }

    protected void onError(Exception e) {
        synchronized (connectionListeners) {
            for (ConnectionListener l : connectionListeners) {
                l.onError(this, new ConnectionException(e));
            }
        }
    }

    public void close() throws IOException {
        close(true, PredefinedDisconnectReason.createReason(PredefinedDisconnectReason.CONNECTION_CLOSED, false));
    }

    private void close(boolean graceful, DisconnectReason reason) throws IOException {
        running = false;
        onDisconn(reason);
        if (graceful) {
            try {
                sendMsg(ControlMessage.DISCONNECT);
            } catch (Exception e) {
                onError(e);
            }
        }
        out.close();
        in.close();
        socket.close();

    }

    public String getPeer() {
        return peer;
    }

    void startHeartbeat() {
        if (!heartBeating.getAndSet(true)) {
            log.debug("Starting Heartbeat");
            executorService.submit(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(30000);
                        while (running) {
                            try {
                                log.debug("TX Heartbeat");
                                sendMsg(ControlMessage.HEARTBEAT);
                                Thread.sleep(30000);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (InterruptedException e) {
                    }
                }
            });
        }
    }

    public void listen() throws ConnectionException {
        if (listening.getAndSet(true))
            throw new ConnectionException("Already Listening!");
        executorService.submit(inputStreamListener);
    }

    private class InputStreamListener implements Runnable {
        @Override
        public void run() {
            while (running) {
                try {
                    Message msg = (Message) in.readObject();
                    onMessage(msg);
                } catch (ClassNotFoundException | IOException e) {
                    if (e instanceof SocketTimeoutException) {
                        onTimeout();
                    } else {
                        if (running) {
                            onError(new ConnectionException(e));
                            if (e instanceof EOFException) {
                                try {
                                    close(false, PredefinedDisconnectReason.RESET);
                                } catch (IOException e1) {
                                    e1.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
