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

package bisq.core.trade.protocol.bisq_v1.tasks.taker;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxResponse;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.TaskRunner;

import java.security.PublicKey;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.TradeValidation.*;
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
            checkNotNull(response);

            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            TradingPeer tradingPeer = processModel.getTradePeer();
            Offer offer = checkNotNull(processModel.getOffer(), "Offer must not be null");

            // 1.7.0: We do not expect the payment account anymore but in case peer has not updated we still process it.
            // TODO This is now always null and the field can be removed from the message
            Optional.ofNullable(response.getMakerPaymentAccountPayload())
                    .ifPresent(e -> tradingPeer.setPaymentAccountPayload(response.getMakerPaymentAccountPayload()));

            // Those 2 fields are actually not null anymore as old versions cannot trade anymore
            // TODO remove the nullable annotation
            Optional.ofNullable(response.getHashOfMakersPaymentAccountPayload())
                    .ifPresent(e -> tradingPeer.setHashOfPaymentAccountPayload(response.getHashOfMakersPaymentAccountPayload()));
            Optional.ofNullable(response.getMakersPaymentMethodId())
                    .ifPresent(e -> tradingPeer.setPaymentMethodId(response.getMakersPaymentMethodId()));

            tradingPeer.setAccountId(response.getMakerAccountId());

            byte[] makerMultiSigPubKey = checkMultiSigPubKey(response.getMakerMultiSigPubKey());
            tradingPeer.setMultiSigPubKey(makerMultiSigPubKey);

            tradingPeer.setContractAsJson(response.getMakerContractAsJson());

            String makerContractSignature = checkBase64Signature(response.getMakerContractSignature());
            tradingPeer.setContractSignature(makerContractSignature);

            String makerPayoutAddressString = checkBitcoinAddress(response.getMakerPayoutAddressString(), btcWalletService);
            tradingPeer.setPayoutAddressString(makerPayoutAddressString);

            List<RawTransactionInput> makerRawTransactionInputs = checkMakersRawTransactionInputs(response.getMakerInputs(),
                    btcWalletService,
                    offer);
            tradingPeer.setRawTransactionInputs(makerRawTransactionInputs);

            byte[] preparedDepositTx = checkSerializedTransaction(response.getPreparedDepositTx(), btcWalletService);
            processModel.setPreparedDepositTx(preparedDepositTx);

            boolean isBlockchain = offer.getPaymentMethod().isBlockchain();
            long lockTime = checkLockTime(response.getLockTime(), isBlockchain, btcWalletService);
            trade.setLockTime(lockTime);

            long delay = btcWalletService.getBestChainHeight() - lockTime;
            log.info("lockTime={}, delay={}", lockTime, delay);

            // Maker has to sign preparedDepositTx. He cannot manipulate the preparedDepositTx - so we avoid to have a
            // challenge protocol for passing the nonce we want to get signed.
            PubKeyRing makerPubKeyRing = checkNotNull(tradingPeer.getPubKeyRing(), "makerPubKeyRing must not be null");
            PublicKey makerSignatureKey = makerPubKeyRing.getSignaturePubKey();
            @SuppressWarnings("UnnecessaryLocalVariable")
            byte[] accountAgeWitnessNonce = preparedDepositTx;
            byte[] accountAgeWitnessSignature = checkSignature(response.getAccountAgeWitnessSignatureOfPreparedDepositTx(),
                    accountAgeWitnessNonce,
                    makerSignatureKey);
            tradingPeer.setAccountAgeWitnessSignature(accountAgeWitnessSignature);

            tradingPeer.setAccountAgeWitnessNonce(accountAgeWitnessNonce);

            long currentDate = checkPeersDate(response.getCurrentDate());
            tradingPeer.setCurrentDate(currentDate);

            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
