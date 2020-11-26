package bisq.core.trade.protocol.tasks.buyer;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Transaction;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerFinalizesDelayedPayoutTx extends TradeTask {
    public BuyerFinalizesDelayedPayoutTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();
            Transaction preparedDepositTx = btcWalletService.getTxFromSerializedTx(processModel.getPreparedDepositTx());
            Transaction preparedDelayedPayoutTx = checkNotNull(processModel.getPreparedDelayedPayoutTx());

            byte[] buyerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(buyerMultiSigPubKey,
                    btcWalletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG).getPubKey()),
                    "buyerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);
            byte[] sellerMultiSigPubKey = processModel.getTradingPeer().getMultiSigPubKey();

            byte[] buyerSignature = processModel.getDelayedPayoutTxSignature();
            byte[] sellerSignature = processModel.getTradingPeer().getDelayedPayoutTxSignature();

            Transaction signedDelayedPayoutTx = processModel.getTradeWalletService().finalizeUnconnectedDelayedPayoutTx(
                    preparedDelayedPayoutTx,
                    buyerMultiSigPubKey,
                    sellerMultiSigPubKey,
                    buyerSignature,
                    sellerSignature,
                    preparedDepositTx.getOutput(0).getValue());

            trade.applyDelayedPayoutTxBytes(signedDelayedPayoutTx.bitcoinSerialize());
            log.info("DelayedPayoutTxBytes = {}", Utilities.bytesAsHexString(trade.getDelayedPayoutTxBytes()));

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
