package bisq.core.btc.low;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.net.BlockingClientManager;

final public class PeerGroup extends PeerGroupProxy {

    // These constructors will be subsequently factored out of this class

    public PeerGroup(
            NetworkParameters params,
            BlockChain vChain
    ) {

        super(new org.bitcoinj.core.PeerGroup(params, vChain));
    }

    public PeerGroup(
            NetworkParameters params,
            BlockChain vChain,
            BlockingClientManager blockingClientManager
    ) {
        super(new org.bitcoinj.core.PeerGroup(params, vChain, blockingClientManager));
    }

}
