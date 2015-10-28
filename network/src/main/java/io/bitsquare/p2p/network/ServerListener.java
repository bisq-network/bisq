package io.bitsquare.p2p.network;

public interface ServerListener {
    void onSocketHandler(Connection connection);
}
