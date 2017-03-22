package io.bisq.network.p2p.peers;

import io.bisq.protobuffer.payload.p2p.NodeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class BanList {
    private static final Logger log = LoggerFactory.getLogger(BanList.class);
    private static List<NodeAddress> list = new ArrayList<>();

    public static List<NodeAddress> getList() {
        return list;
    }

    public static void setList(List<NodeAddress> list) {
        BanList.list = list;
    }

    public static void add(NodeAddress onionAddress) {
        list.add(onionAddress);
    }

    public static void remove(NodeAddress onionAddress) {
        list.add(onionAddress);
    }

    public static boolean contains(NodeAddress nodeAddress) {
        return list.contains(nodeAddress);
    }
}
