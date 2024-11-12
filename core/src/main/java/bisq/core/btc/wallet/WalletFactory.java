package bisq.core.btc.wallet;

import bisq.core.btc.setup.BisqKeyChainGroupStructure;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.KeyChainGroupStructure;
import org.bitcoinj.wallet.Wallet;

public class WalletFactory {
    private final NetworkParameters networkParams;

    public WalletFactory(NetworkParameters networkParams) {
        this.networkParams = networkParams;
    }

    public Wallet createBtcWallet() {
        return createWallet(false);
    }

    public Wallet createBsqWallet() {
        return createWallet(true);
    }

    private Wallet createWallet(boolean isBsqWallet) {
        KeyChainGroupStructure structure = new BisqKeyChainGroupStructure(isBsqWallet);
        KeyChainGroup.Builder kcgBuilder = KeyChainGroup.builder(networkParams, structure);

        Script.ScriptType preferredOutputScriptType = Script.ScriptType.P2WPKH;
        kcgBuilder.fromRandom(preferredOutputScriptType);
        return new Wallet(networkParams, kcgBuilder.build());
    }
}
