package io.bisq.network;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class exists because the TorNetworkNode in module 'network' can't access the preferences
 * in 'core' directly, so we use this provider.
 */
@Slf4j
public class BridgeProvider {
    @Setter
    @Getter
    static List<String> bridges = new ArrayList<>();
}