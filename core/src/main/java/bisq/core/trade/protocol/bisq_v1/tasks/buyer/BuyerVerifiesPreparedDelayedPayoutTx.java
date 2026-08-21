/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.protocol.bisq_v1.tasks.buyer;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.offer.Offer;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.validation.DelayedPayoutTxValidation;
import bisq.core.trade.validation.MinerFeeValidation;

import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Transaction;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.DelayedPayoutTxValidation.checkDelayedPayoutTx;
import static bisq.core.trade.validation.DelayedPayoutTxValidation.checkDelayedPayoutTxInput;
import static bisq.core.trade.validation.DelayedPayoutTxValidation.checkDelayedPayoutTxInputAmount;
import static bisq.core.trade.validation.DelayedPayoutTxValidation.checkLockTime;
import static bisq.core.trade.validation.DepositTxValidation.checkRawTransactionInputsAreNotMalleable;
import static bisq.core.trade.validation.TransactionValidation.toVerifiedTransaction;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerVerifiesPreparedDelayedPayoutTx extends TradeTask {
    public BuyerVerifiesPreparedDelayedPayoutTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            Offer offer = processModel.getOffer();
            FeeService feeService = processModel.getFeeService();
            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            DelayedPayoutTxReceiverService delayedPayoutTxReceiverService = processModel.getDelayedPayoutTxReceiverService();

            int burningManSelectionHeight = DelayedPayoutTxValidation.checkBurningManSelectionHeight(processModel.getBurningManSelectionHeight(),
                    delayedPayoutTxReceiverService);

            long tradeTxFeeAsLong = MinerFeeValidation.checkTradeTxFeeIsInTolerance(trade.getTradeTxFeeAsLong(), feeService);

            Transaction preparedDepositTx = toVerifiedTransaction(processModel.getPreparedDepositTx(), btcWalletService);
            long multisigOutputAmount = preparedDepositTx.getOutput(0).getValue().value;
            long inputAmount = checkDelayedPayoutTxInputAmount(multisigOutputAmount, trade, tradeTxFeeAsLong);

            Transaction peersPreparedDelayedPayoutTx = checkNotNull(processModel.getPreparedDelayedPayoutTx());
            checkDelayedPayoutTx(peersPreparedDelayedPayoutTx, trade, btcWalletService);

            int burningManAddressListVersion = processModel.getBurningManAddressListVersion();
            List<Tuple2<Long, String>> delayedPayoutTxReceivers = delayedPayoutTxReceiverService.getReceivers(
                    burningManSelectionHeight,
                    inputAmount,
                    tradeTxFeeAsLong,
                    burningManAddressListVersion);
            delayedPayoutTxReceiverService.validateDelayedPayoutTxReceivers(
                    delayedPayoutTxReceivers,
                    burningManAddressListVersion);

            boolean isAltcoin = offer.getPaymentMethod().isBlockchain();
            long lockTime = checkLockTime(trade.getLockTime(), isAltcoin, btcWalletService);

            Transaction myPreparedDelayedPayoutTx = tradeWalletService.createDelayedUnsignedPayoutTx(
                    preparedDepositTx,
                    delayedPayoutTxReceivers,
                    lockTime);
            if (!myPreparedDelayedPayoutTx.getTxId().equals(peersPreparedDelayedPayoutTx.getTxId())) {
                String errorMsg = "TxIds of myPreparedDelayedPayoutTx and peersPreparedDelayedPayoutTx must be the same.";
                DaoFacade daoFacade = processModel.getDaoFacade();
                log.error("{} \nmyPreparedDelayedPayoutTx={}, \npeersPreparedDelayedPayoutTx={}, " +
                                "\nBtcWalletService.chainHeight={}, " +
                                "\nDaoState.chainHeight={}, " +
                                "\nisDaoStateIsInSync={}",
                        errorMsg, myPreparedDelayedPayoutTx, peersPreparedDelayedPayoutTx,
                        btcWalletService.getBestChainHeight(),
                        daoFacade.getChainHeight(),
                        daoFacade.isDaoStateReadyAndInSync());
                throw new IllegalArgumentException(errorMsg);
            }

            List<RawTransactionInput> myRawTransactionInputs = processModel.getRawTransactionInputs();
            checkRawTransactionInputsAreNotMalleable(myRawTransactionInputs, tradeWalletService);

            List<RawTransactionInput> peersRawTransactionInputs = processModel.getTradePeer().getRawTransactionInputs();
            checkRawTransactionInputsAreNotMalleable(peersRawTransactionInputs, tradeWalletService);

            checkDelayedPayoutTxInput(peersPreparedDelayedPayoutTx, preparedDepositTx);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
