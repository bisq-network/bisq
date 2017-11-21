package io.bisq.network.p2p.peers;

import com.google.inject.name.Named;
import io.bisq.network.NetworkOptionKeys;
import io.bisq.network.p2p.NodeAddress;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BanList {
    private static List<NodeAddress> list = new ArrayList<>();

    public static void add(NodeAddress onionAddress) {
        list.add(onionAddress);
    }

    public static boolean isBanned(NodeAddress nodeAddress) {
        return list.contains(nodeAddress);
    }

    @Inject
    public BanList(@Named(NetworkOptionKeys.BAN_LIST) String banList) {
        if (banList != null && !banList.isEmpty())
            BanList.list =Arrays.asList(StringUtils.deleteWhitespace(banList).split(",")).stream().map(NodeAddress::new).collect(Collectors.toList());
    }
}
