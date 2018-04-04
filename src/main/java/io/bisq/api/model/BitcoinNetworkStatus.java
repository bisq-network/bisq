package io.bisq.api.model;

import io.bisq.core.btc.BitcoinNodes;

import java.util.List;

public class BitcoinNetworkStatus {

    public BitcoinNodes.BitcoinNodesOption bitcoinNodesOption;

    public String bitcoinNodes;

    public List<String> peers;

    public boolean useTorForBitcoinJ;
}
