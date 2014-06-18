package io.bitsquare.msg.listeners;

import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;

import java.util.Map;

public interface ArbitratorListener
{
    void onArbitratorAdded(Data offerData, boolean success);

    void onArbitratorsReceived(Map<Number160, Data> dataMap, boolean success);

    void onArbitratorRemoved(Data data, boolean success);
}