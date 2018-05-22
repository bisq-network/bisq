package network.bisq.api.model;

import java.util.List;

public class P2PNetworkStatus {

    public String address;

    public List<P2PNetworkConnection> p2pNetworkConnection;

    public long totalReceivedBytes;

    public long totalSentBytes;
}
