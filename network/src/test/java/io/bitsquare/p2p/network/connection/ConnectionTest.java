package io.bitsquare.p2p.network.connection;

import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.connection.CloseConnectionReason;
import io.bitsquare.p2p.network.connection.Connection;
import io.bitsquare.p2p.network.connection.ConnectionListener;
import io.bitsquare.p2p.network.connection.MessageListener;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import mockit.integration.junit4.JMockit;
import org.bitcoinj.core.Message;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Created by mike on 29/06/16.
 */
@RunWith(JMockit.class)
public class ConnectionTest {


    @Test
    public void constructor(@Mocked Socket socket, @Mocked MessageListener messageListener, @Mocked ConnectionListener connectionListener) throws IOException {
//        MessageListener messageListener = new MessageListener() {
//            @Override
//            public void onMessage(io.bitsquare.p2p.Message message, Connection connection) {
//                System.out.println("on message:" + message.toString());
//            }
//        };
//        ConnectionListener connectionListener = new ConnectionListener() {
//            @Override
//            public void onConnection(Connection connection) {
//                System.out.println("on connection");
//
//            }
//
//            @Override
//            public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
//                System.out.println("on disconnect");
//
//            }
//
//            @Override
//            public void onError(Throwable throwable) {
//                System.out.println("on error");
//
//            }
//        };
        NodeAddress nodeAddress = new NodeAddress("localhost:80");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(new byte[100]);

        new Expectations() {{
            socket.getOutputStream();
            result = byteArrayOutputStream;
            socket.getInputStream();
            result = byteArrayInputStream;
        }};

        Connection connection = new Connection(socket, messageListener, connectionListener, nodeAddress);

        new Verifications() {{
            messageListener.onMessage((io.bitsquare.p2p.Message) any, connection);
            times = 1;
        }};
    }

}