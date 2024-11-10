package bisq.core;

import bisq.core.btc.wallet.WalletFactory;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.Wallet;

import java.nio.file.Path;

import java.util.List;

import lombok.Getter;

@Getter
public class RegtestWalletAppKit {
    private final WalletAppKit walletAppKit;

    public RegtestWalletAppKit(NetworkParameters networkParams, Path dataDirPath, List<Wallet> wallets) {
        walletAppKit = new WalletAppKit(networkParams, dataDirPath.toFile(), "dataDirFilePrefix") {
            @Override
            protected void onSetupCompleted() {
                super.onSetupCompleted();
                wallets.forEach(wallet -> {
                    vChain.addWallet(wallet);
                    vPeerGroup.addWallet(wallet);
                });
            }
        };
    }

    public void initialize() {
        walletAppKit.connectToLocalHost();

        var walletFactory = new WalletFactory(walletAppKit.params());
        walletAppKit.setWalletFactory((params, keyChainGroup) -> walletFactory.createBsqWallet());

        walletAppKit.startAsync();
        walletAppKit.awaitRunning();
    }
}
