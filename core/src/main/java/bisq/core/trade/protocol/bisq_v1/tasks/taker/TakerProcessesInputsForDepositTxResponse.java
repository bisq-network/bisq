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
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxResponse;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.validation.DelayedPayoutTxValidation;
import bisq.core.trade.validation.DepositTxValidation;
import bisq.core.trade.validation.TransactionValidation;

import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import java.security.PublicKey;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.DsaSignatureValidation.checkBase64DSASignature;
import static bisq.core.trade.validation.DsaSignatureValidation.checkDSASignature;
import static bisq.core.trade.validation.TradeValidation.checkPeersDate;
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
            DelayedPayoutTxReceiverService delayedPayoutTxReceiverService = processModel.getDelayedPayoutTxReceiverService();
            TradingPeer tradingPeer = processModel.getTradePeer();
            Offer offer = checkNotNull(processModel.getOffer(), "Offer must not be null");

            tradingPeer.setHashOfPaymentAccountPayload(response.getHashOfMakersPaymentAccountPayload());
            tradingPeer.setPaymentMethodId(response.getMakersPaymentMethodId());
            List<Integer> supportedBurningManAddressListVersions = response.getSupportedBurningManAddressListVersions();
            tradingPeer.setSupportedBurningManAddressListVersions(supportedBurningManAddressListVersions);
            int burningManAddressListVersion = delayedPayoutTxReceiverService.selectBurningManAddressListVersion(
                    supportedBurningManAddressListVersions);
            processModel.setBurningManAddressListVersion(burningManAddressListVersion);

            tradingPeer.setAccountId(response.getMakerAccountId());

            byte[] makerMultiSigPubKey = TransactionValidation.checkMultiSigPubKey(response.getMakerMultiSigPubKey());
            tradingPeer.setMultiSigPubKey(makerMultiSigPubKey);

            tradingPeer.setContractAsJson(response.getMakerContractAsJson());

            String makerContractSignature = checkBase64DSASignature(response.getMakerContractSignature());
            tradingPeer.setContractSignature(makerContractSignature);

            String makerPayoutAddressString = TransactionValidation.checkBitcoinAddress(response.getMakerPayoutAddressString(), btcWalletService);
            tradingPeer.setPayoutAddressString(makerPayoutAddressString);

            List<RawTransactionInput> makerRawTransactionInputs = DepositTxValidation.checkMakersRawTransactionInputs(response.getMakerInputs(),
                    btcWalletService,
                    offer);
            tradingPeer.setRawTransactionInputs(makerRawTransactionInputs);

            // We expect the prepared deposit transaction to be unsigned
            byte[] preparedDepositTx = DepositTxValidation.checkTransactionIsUnsigned(response.getPreparedDepositTx(), btcWalletService);
            Transaction parsedPreparedDepositTx = TransactionValidation.toVerifiedTransaction(preparedDepositTx, btcWalletService);
            DepositTxValidation.checkMakersPreparedDepositTx(parsedPreparedDepositTx,
                    offer,
                    checkNotNull(trade.getAmount(), "trade.getAmount() must not be null"),
                    trade.getTradeTxFee(),
                    makerRawTransactionInputs,
                    checkNotNull(processModel.getRawTransactionInputs(), "takerRawTransactionInputs must not be null"),
                    makerMultiSigPubKey,
                    checkNotNull(processModel.getMyMultiSigPubKey(), "processModel.getMyMultiSigPubKey() must not be null"),
                    btcWalletService.getParams());
            processModel.setPreparedDepositTx(preparedDepositTx);

            boolean isAltcoin = offer.getPaymentMethod().isBlockchain();
            long lockTime = DelayedPayoutTxValidation.checkLockTime(response.getLockTime(), isAltcoin, btcWalletService);
            trade.setLockTime(lockTime);

            long delay = btcWalletService.getBestChainHeight() - lockTime;
            log.info("lockTime={}, delay={}", lockTime, delay);

            // Maker has to sign preparedDepositTx. He cannot manipulate the preparedDepositTx - so we avoid to have a
            // challenge protocol for passing the nonce we want to get signed.
            PubKeyRing makerPubKeyRing = checkNotNull(tradingPeer.getPubKeyRing(), "makerPubKeyRing must not be null");
            PublicKey makerSignatureKey = makerPubKeyRing.getSignaturePubKey();
            @SuppressWarnings("UnnecessaryLocalVariable")
            byte[] accountAgeWitnessNonce = preparedDepositTx;
            byte[] accountAgeWitnessSignature = checkDSASignature(response.getAccountAgeWitnessSignatureOfPreparedDepositTx(),
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
