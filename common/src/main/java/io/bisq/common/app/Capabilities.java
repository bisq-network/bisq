package io.bisq.common.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;

public class Capabilities {
    private static final Logger log = LoggerFactory.getLogger(Capabilities.class);

    // We can define here special features the client is supporting. 
    // Useful for updates to new versions where a new data type would break backwards compatibility or to 
    // limit a node to certain behaviour and roles like the seed nodes.
    // We don't use the Enum in any serialized data, as changes in the enum would break backwards compatibility. We use the ordinal integer instead.
    // Sequence in the enum must not be changed (append only).
    public enum Capability {
        TRADE_STATISTICS
    }

    public static void setCapabilities(ArrayList<Integer> capabilities) {
        Capabilities.capabilities = capabilities;
    }

    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    private static ArrayList<Integer> capabilities = new ArrayList<>(Arrays.asList(
            Capability.TRADE_STATISTICS.ordinal()
    ));

    /**
     * @return The Capabilities as ordinal integer the client supports.
     */
    public static ArrayList<Integer> getCapabilities() {
        return capabilities;
    }
}
