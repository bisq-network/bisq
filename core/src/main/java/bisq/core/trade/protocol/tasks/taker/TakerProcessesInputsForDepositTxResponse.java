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

package bisq.core.trade.protocol.tasks.taker;

import bisq.core.btc.wallet.Restrictions;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.InputsForDepositTxResponse;
import bisq.core.trade.protocol.TradingPeer;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.config.Config;
import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.util.Validator.checkTradeId;
import static bisq.core.util.Validator.nonEmptyStringOf;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TakerProcessesInputsForDepositTxResponse extends TradeTask {
    public TakerProcessesInputsForDepositTxResponse(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            InputsForDepositTxResponse response = (InputsForDepositTxResponse) processModel.getTradeMessage();
            checkTradeId(processModel.getOfferId(), response);
            checkNotNull(response);

            TradingPeer tradingPeer = processModel.getTradingPeer();
            tradingPeer.setPaymentAccountPayload(checkNotNull(response.getMakerPaymentAccountPayload()));
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
                int myLockTime = processModel.getBtcWalletService().getBestChainHeight() +
                        Restrictions.getLockTime(processModel.getOffer().getPaymentMethod().isAsset());
                // We allow a tolerance of 3 blocks as BestChainHeight might be a bit different on maker and taker in case a new
                // block was just found
                checkArgument(Math.abs(lockTime - myLockTime) <= 3,
                        "Lock time of maker is more than 3 blocks different to the lockTime I " +
                                "calculated. Makers lockTime= " + lockTime + ", myLockTime=" + myLockTime);
            }
            trade.setLockTime(lockTime);
            long delay = processModel.getBtcWalletService().getBestChainHeight() - lockTime;
            log.info("lockTime={}, delay={}", lockTime, delay);

            // Maker has to sign preparedDepositTx. He cannot manipulate the preparedDepositTx - so we avoid to have a
            // challenge protocol for passing the nonce we want to get signed.
            tradingPeer.setAccountAgeWitnessNonce(preparedDepositTx);
            tradingPeer.setAccountAgeWitnessSignature(checkNotNull(response.getAccountAgeWitnessSignatureOfPreparedDepositTx()));

            tradingPeer.setCurrentDate(response.getCurrentDate());

            checkArgument(response.getMakerInputs().size() > 0);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
