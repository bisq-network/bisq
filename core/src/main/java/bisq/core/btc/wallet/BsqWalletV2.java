package bisq.core.btc.wallet;

import bisq.core.btc.exceptions.BsqChangeBelowDustException;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.Wallet;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class BsqWalletV2 {
    private final NetworkParameters networkParams;
    private final PeerGroup peerGroup;

    private final BtcWalletV2 btcWallet;
    private final Wallet bsqWallet;
    private final BsqCoinSelector bsqCoinSelector;

    public BsqWalletV2(NetworkParameters networkParams,
                       PeerGroup peerGroup,
                       BtcWalletV2 btcWallet,
                       Wallet bsqWallet,
                       BsqCoinSelector bsqCoinSelector) {
        this.networkParams = networkParams;
        this.peerGroup = peerGroup;
        this.btcWallet = btcWallet;
        this.bsqWallet = bsqWallet;
        this.bsqCoinSelector = bsqCoinSelector;
    }

    public TransactionBroadcast sendBsq(Address receiverAddress,
                                        Coin receiverAmount,
                                        Coin txFee) throws InsufficientMoneyException, BsqChangeBelowDustException {
        checkArgument(Restrictions.isAboveDust(receiverAmount),
                "The amount is too low (dust limit).");

        Transaction bsqTx = new Transaction(networkParams);
        bsqTx.addOutput(receiverAmount, receiverAddress);

        CoinSelection selection = bsqCoinSelector.select(receiverAmount, bsqWallet.calculateAllSpendCandidates());
        if (selection.valueGathered.isLessThan(receiverAmount)) {
            throw new InsufficientMoneyException(receiverAmount, "Wallet doesn't have " + receiverAmount + " BSQ.");
        }
        selection.gathered.forEach(bsqTx::addInput);

        Coin change = bsqCoinSelector.getChange(receiverAmount, selection);
        if (Restrictions.isAboveDust(change)) {
            Address changeAddress = bsqWallet.freshReceiveAddress();
            bsqTx.addOutput(change, changeAddress);
        } else if (!change.isZero()) {
            String msg = "BSQ change output is below dust limit. outputValue=" + change.value / 100 + " BSQ";
            log.warn(msg);
            throw new BsqChangeBelowDustException(msg, change);
        }

        BisqTransactionSigner.sign(bsqWallet, bsqTx, 0);
        bsqCoinSelector.setUtxoCandidates(null);   // We reuse the selectors. Reset the transactionOutputCandidates field

        Transaction finalTx = btcWallet.addMiningFeesToBsqTx(bsqTx, txFee);
        return peerGroup.broadcastTransaction(finalTx);
    }
}
