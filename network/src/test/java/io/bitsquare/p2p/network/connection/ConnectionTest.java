package io.bitsquare.p2p.network.connection;

import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.connection.CloseConnectionReason;
import io.bitsquare.p2p.network.connection.Connection;
import io.bitsquare.p2p.network.connection.ConnectionListener;
import io.bitsquare.p2p.network.connection.MessageListener;
import org.junit.Test;

import java.net.Socket;

/**
 * Created by mike on 29/06/16.
 */
public class ConnectionTest {


    @Test
    public void constructor() {
        Socket socket = new Socket();
        MessageListener messageListener = new MessageListener() {
            @Override
            public void onMessage(io.bitsquare.p2p.Message message, Connection connection) {
                System.out.println("on message:" + message.toString());
            }
        };
        ConnectionListener connectionListener = new ConnectionListener() {
            @Override
            public void onConnection(Connection connection) {
                System.out.println("on connection");

            }

            @Override
            public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
                System.out.println("on disconnect");

            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println("on error");

            }
        };
        NodeAddress nodeAddress = new NodeAddress("localhost:80");
        Connection connection = new Connection(socket, messageListener, connectionListener, nodeAddress);
    }

}