package io.nucleo.net.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class BridgeProvider {
    private static final Logger log = LoggerFactory.getLogger(BridgeProvider.class);

    static List<String> bridges = getDefaultBridges();

    private static List<String> getDefaultBridges() {
        return Arrays.asList(
                // private bridge
                "bridge 158.69.102.94:443 C2FA8AE17D5115B493CC694C0D9851FD4166B11D");
    }

    public static List<String> getBridges() {
        return bridges;
    }

    public static void setBridges(List<String> bridges) {
        BridgeProvider.bridges = bridges;
    }
}
