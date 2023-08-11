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

package bisq.core.trade.protocol.bisq_v5.tasks.seller_as_taker;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v5.messages.InputsForDepositTxResponse_v5;

import bisq.common.config.Config;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.util.Validator.checkTradeId;
import static bisq.core.util.Validator.nonEmptyStringOf;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerAsTakerProcessesInputsForDepositTxResponse_v5 extends TradeTask {
    public SellerAsTakerProcessesInputsForDepositTxResponse_v5(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            TradingPeer tradingPeer = processModel.getTradePeer();

            InputsForDepositTxResponse_v5 response = (InputsForDepositTxResponse_v5) processModel.getTradeMessage();
            checkTradeId(processModel.getOfferId(), response);
            checkNotNull(response);


            Optional.ofNullable(response.getHashOfMakersPaymentAccountPayload())
                    .ifPresent(e -> tradingPeer.setHashOfPaymentAccountPayload(response.getHashOfMakersPaymentAccountPayload()));
            Optional.ofNullable(response.getMakersPaymentMethodId())
                    .ifPresent(e -> tradingPeer.setPaymentMethodId(response.getMakersPaymentMethodId()));

            tradingPeer.setAccountId(nonEmptyStringOf(response.getMakerAccountId()));
            tradingPeer.setMultiSigPubKey(checkNotNull(response.getMakerMultiSigPubKey()));
            tradingPeer.setContractAsJson(nonEmptyStringOf(response.getMakerContractAsJson()));
            tradingPeer.setContractSignature(nonEmptyStringOf(response.getMakerContractSignature()));
            tradingPeer.setPayoutAddressString(nonEmptyStringOf(response.getMakerPayoutAddressString()));
            tradingPeer.setRawTransactionInputs(checkNotNull(response.getMakerInputs()));
            byte[] preparedDepositTx = checkNotNull(response.getPreparedDepositTx());
            processModel.setPreparedDepositTx(preparedDepositTx);
            long lockTime = response.getLockTime();
            if (Config.baseCurrencyNetwork().isMainnet()) {
                int myLockTime = btcWalletService.getBestChainHeight() +
                        Restrictions.getLockTime(processModel.getOffer().getPaymentMethod().isBlockchain());
                // We allow a tolerance of 3 blocks as BestChainHeight might be a bit different on maker and taker in case a new
                // block was just found
                checkArgument(Math.abs(lockTime - myLockTime) <= 3,
                        "Lock time of maker is more than 3 blocks different to the lockTime I " +
                                "calculated. Makers lockTime= " + lockTime + ", myLockTime=" + myLockTime);
            }
            trade.setLockTime(lockTime);
            long delay = btcWalletService.getBestChainHeight() - lockTime;
            log.info("lockTime={}, delay={}", lockTime, delay);

            // Maker has to sign preparedDepositTx. He cannot manipulate the preparedDepositTx - so we avoid to have a
            // challenge protocol for passing the nonce we want to get signed.
            tradingPeer.setAccountAgeWitnessNonce(preparedDepositTx);
            tradingPeer.setAccountAgeWitnessSignature(checkNotNull(response.getAccountAgeWitnessSignatureOfPreparedDepositTx()));

            tradingPeer.setCurrentDate(response.getCurrentDate());

            checkArgument(response.getMakerInputs().size() > 0);

            byte[] tx = checkNotNull(response.getBuyersUnsignedWarningTx());
            Transaction buyersUnsignedWarningTx = btcWalletService.getTxFromSerializedTx(tx);
            tradingPeer.setWarningTx(buyersUnsignedWarningTx);
            tradingPeer.setWarningTxBuyerSignature(response.getBuyersWarningTxSignature());

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
