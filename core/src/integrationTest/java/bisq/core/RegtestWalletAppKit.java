package bisq.core;

import bisq.core.btc.wallet.WalletFactory;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.Wallet;

import java.nio.file.Path;

import java.util.List;

import lombok.Getter;

@Getter
public class RegtestWalletAppKit {
    private final WalletAppKit walletAppKit;
    private final WalletFactory walletFactory;

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

        walletFactory = new WalletFactory(networkParams);
    }

    public void initialize() {
        walletAppKit.connectToLocalHost();
        walletAppKit.setWalletFactory((params, keyChainGroup) -> walletFactory.createBsqWallet());

        walletAppKit.startAsync();
        walletAppKit.awaitRunning();
    }

    public Wallet createNewBsqWallet() {
        Wallet bsqWallet = walletFactory.createBsqWallet();
        BlockChain blockChain = walletAppKit.chain();
        blockChain.addWallet(bsqWallet);

        PeerGroup peerGroup = walletAppKit.peerGroup();
        peerGroup.addWallet(bsqWallet);
        return bsqWallet;
    }
}
