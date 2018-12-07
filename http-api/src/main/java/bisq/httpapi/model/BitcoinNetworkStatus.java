package bisq.httpapi.model;

import bisq.core.btc.nodes.BtcNodes;

import java.util.List;

public class BitcoinNetworkStatus {

    public BtcNodes.BitcoinNodesOption bitcoinNodesOption;

    public String bitcoinNodes;

    public List<String> peers;

    public boolean useTorForBitcoinJ;
}
