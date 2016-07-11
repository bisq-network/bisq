package io.bitsquare.p2p.network.connection;

import io.bitsquare.app.Log;
import io.bitsquare.p2p.network.RuleViolation;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.OptionalDataException;
import java.io.StreamCorruptedException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Holds all shared data between Connection and InputHandler
 * Runs in same thread as Connection
 */
public class SharedModel {
    private static final Logger log = LoggerFactory.getLogger(SharedModel.class);

    @Getter
    private final Connection connection;
    @Getter
    private final Socket socket;
    private final ConcurrentHashMap<RuleViolation, Integer> ruleViolations = new ConcurrentHashMap<>();

    // mutable
    private volatile boolean stopped;
    private CloseConnectionReason closeConnectionReason;
    @Getter
    private RuleViolation ruleViolation;

    public SharedModel(Connection connection, Socket socket) {
        this.connection = connection;
        this.socket = socket;
    }

    public boolean reportInvalidRequest(RuleViolation ruleViolation) {
        log.warn("We got reported an corrupt request " + ruleViolation + "\n\tconnection=" + this);
        int numRuleViolations;
        if (ruleViolations.contains(ruleViolation))
            numRuleViolations = ruleViolations.get(ruleViolation);
        else
            numRuleViolations = 0;

        numRuleViolations++;
        ruleViolations.put(ruleViolation, numRuleViolations);

        if (numRuleViolations >= ruleViolation.maxTolerance) {
            log.warn("We close connection as we received too many corrupt requests.\n" +
                    "numRuleViolations={}\n\t" +
                    "corruptRequest={}\n\t" +
                    "corruptRequests={}\n\t" +
                    "connection={}", numRuleViolations, ruleViolation, ruleViolations.toString(), connection);
            this.ruleViolation = ruleViolation;
            shutDown(CloseConnectionReason.RULE_VIOLATION);
            return true;
        } else {
            return false;
        }
    }

    public void handleConnectionException(Throwable e) {
        Log.traceCall(e.toString());
        if (e instanceof SocketException) {
            if (socket.isClosed())
                closeConnectionReason = CloseConnectionReason.SOCKET_CLOSED;
            else
                closeConnectionReason = CloseConnectionReason.RESET;
        } else if (e instanceof SocketTimeoutException || e instanceof TimeoutException) {
            closeConnectionReason = CloseConnectionReason.SOCKET_TIMEOUT;
            log.debug("SocketTimeoutException at socket " + socket.toString() + "\n\tconnection={}" + this);
        } else if (e instanceof EOFException) {
            closeConnectionReason = CloseConnectionReason.TERMINATED;
        } else if (e instanceof OptionalDataException || e instanceof StreamCorruptedException) {
            closeConnectionReason = CloseConnectionReason.CORRUPTED_DATA;
        } else {
            // TODO sometimes we get StreamCorruptedException, OptionalDataException, IllegalStateException
            closeConnectionReason = CloseConnectionReason.UNKNOWN_EXCEPTION;
            log.warn("Unknown reason for exception at socket {}\n\t" +
                            "connection={}\n\t" +
                            "Exception=",
                    socket.toString(),
                    this,
                    e.toString());
            e.printStackTrace();
        }

        shutDown(closeConnectionReason);
    }

    public void shutDown(CloseConnectionReason closeConnectionReason) {
        if (!stopped) {
            stopped = true;
            connection.shutDown(closeConnectionReason);
        }
    }

    public void stop() {
        this.stopped = true;
    }

    @Override
    public String toString() {
        return "SharedSpace{" +
                "socket=" + socket +
                ", ruleViolations=" + ruleViolations +
                '}';
    }
}