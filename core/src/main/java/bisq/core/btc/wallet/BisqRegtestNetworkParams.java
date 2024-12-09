package bisq.core.btc.wallet;

import org.bitcoinj.params.RegTestParams;

public class BisqRegtestNetworkParams extends RegTestParams {
    public void setPort(int port) {
        this.port = port;
    }
}
