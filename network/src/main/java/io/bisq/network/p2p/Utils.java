package io.bisq.network.p2p;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;

public class Utils {
    public static int findFreeSystemPort() {
        try {
            ServerSocket server = new ServerSocket(0);
            int port = server.getLocalPort();
            server.close();
            return port;
        } catch (IOException ignored) {
            return new Random().nextInt(10000) + 50000;
        }
    }
}
