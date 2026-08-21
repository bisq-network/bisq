package bisq.core.btc.wallet;

import bisq.core.btc.exceptions.BsqChangeBelowDustException;
import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.model.BsqTransferModel;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class BsqTransferService {

    private final WalletsManager walletsManager;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;

    @Inject
    public BsqTransferService(WalletsManager walletsManager,
                              BsqWalletService bsqWalletService,
                              BtcWalletService btcWalletService) {
        this.walletsManager = walletsManager;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
    }

    public BsqTransferModel getBsqTransferModel(Address address,
                                                Coin receiverAmount,
                                                Coin txFeePerVbyte)
            throws TransactionVerificationException,
            WalletException,
            BsqChangeBelowDustException,
            InsufficientMoneyException {

        Transaction preparedSendTx = bsqWalletService.getPreparedSendBsqTx(address.toString(), receiverAmount);
        Transaction txWithBtcFee = btcWalletService.completePreparedSendBsqTx(preparedSendTx, txFeePerVbyte);
        Transaction signedTx = bsqWalletService.signTxAndVerifyNoDustOutputs(txWithBtcFee);

        return new BsqTransferModel(address,
                receiverAmount,
                preparedSendTx,
                txWithBtcFee,
                signedTx);
    }

    public void sendFunds(BsqTransferModel bsqTransferModel, TxBroadcaster.Callback callback) {
        log.info("Publishing BSQ transfer {}", bsqTransferModel.toShortString());
        walletsManager.publishAndCommitBsqTx(bsqTransferModel.getTxWithBtcFee(),
                bsqTransferModel.getTxType(),
                callback);
    }
}
