package io.bisq.common.app;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Capabilities {
    // We can define here special features the client is supporting.
    // Useful for updates to new versions where a new data type would break backwards compatibility or to
    // limit a node to certain behaviour and roles like the seed nodes.
    // We don't use the Enum in any serialized data, as changes in the enum would break backwards compatibility. We use the ordinal integer instead.
    // Sequence in the enum must not be changed (append only).
    public enum Capability {
        TRADE_STATISTICS,
        TRADE_STATISTICS_2,
        ACCOUNT_AGE_WITNESS,
        SEED_NODE,
        DAO_FULL_NODE,
    }

    // Application need to set supported capabilities at startup
    @Getter
    @Setter
    private static ArrayList<Integer> supportedCapabilities = new ArrayList<>();

    public static boolean isCapabilitySupported(final List<Integer> requiredItems, final List<Integer> supportedItems) {
        if (requiredItems != null && !requiredItems.isEmpty()) {
            if (supportedItems != null && !supportedItems.isEmpty()) {
                List<Integer> matches = new ArrayList<>();
                for (int requiredItem : requiredItems) {
                    matches.addAll(supportedItems.stream()
                            .filter(supportedItem -> requiredItem == supportedItem)
                            .collect(Collectors.toList()));
                }
                return matches.size() == requiredItems.size();
            } else {
                return false;
            }
        } else {
            return true;
        }
    }
}
