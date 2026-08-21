package bisq.core.btc.wallet;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.CoinSelector;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

public class BtcWalletV2 {
    private final CoinSelector coinSelector;
    private final Wallet wallet;

    public BtcWalletV2(CoinSelector coinSelector, Wallet wallet) {
        this.coinSelector = coinSelector;
        this.wallet = wallet;
    }

    public Transaction addMiningFeesToBsqTx(Transaction bsqTx, Coin txFee) throws InsufficientMoneyException {
        int signOffset = bsqTx.getInputs().size();

        SendRequest sendRequest = SendRequest.forTx(bsqTx);
        sendRequest.ensureMinRequiredFee = true;
        sendRequest.feePerKb = txFee;
        sendRequest.shuffleOutputs = false;
        sendRequest.signInputs = false;
        sendRequest.coinSelector = coinSelector;

        wallet.completeTx(sendRequest);

        Transaction finalTx = sendRequest.tx;
        BisqTransactionSigner.sign(wallet, finalTx, signOffset);
        return finalTx;
    }
}
