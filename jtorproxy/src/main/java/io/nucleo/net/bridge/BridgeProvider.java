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
                "bridge 220.213.33.49:443 BF402231C0E600117354EBA1F6D333EBCF2EB0D0",
                "bridge 95.27.160.46:9201 DDDF9CFCF995E02F8251F740DC32B2836A15B28A",
                "bridge 85.204.57.66:443 E7CA5C41D8D137E00940235B9B7484E7D2138277",
                "bridge 194.132.208.163:17538 255B37D126669DF93A51D767ECF1618BE0393B3A",
                "bridge 80.240.140.199:8443 91C5392AB05F70ACDCC0005EA8A44A181C1478F1",
                "bridge 78.46.223.54:443 C0B6748FD3F781798A264A74D7AB5436E248A309"
        );
    }

    public static List<String> getBridges() {
        return bridges;
    }

    public static void setBridges(List<String> bridges) {
        BridgeProvider.bridges = bridges;
    }
}
