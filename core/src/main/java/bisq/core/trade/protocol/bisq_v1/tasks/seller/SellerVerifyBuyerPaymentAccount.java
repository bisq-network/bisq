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

package bisq.core.trade.protocol.bisq_v1.tasks.seller;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.TaskRunner;

import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;

public class SellerVerifyBuyerPaymentAccount extends TradeTask {
    public SellerVerifyBuyerPaymentAccount(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            Offer offer = checkNotNull(trade.getOffer());
            if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode()) ||
                    processModel.isBuyerPaymentAccountValidated()) {
                complete();
                return;
            }

            TradingPeer tradingPeer = processModel.getTradePeer();
            PaymentAccountPayload paymentAccountPayload = checkNotNull(tradingPeer.getPaymentAccountPayload(),
                    "Buyer payment account was not revealed and validated");
            PubKeyRing pubKeyRing = checkNotNull(tradingPeer.getPubKeyRing(), "Buyer pubKeyRing must not be null");
            byte[] nonce = checkNotNull(tradingPeer.getAccountAgeWitnessNonce(),
                    "Buyer account-age witness nonce must not be null");
            byte[] signature = checkNotNull(tradingPeer.getAccountAgeWitnessSignature(),
                    "Buyer account-age witness signature must not be null");
            long peerDate = tradingPeer.getCurrentDate();
            Date tradeDate = peerDate > 0 ? new Date(peerDate) : trade.getDate();
            AtomicReference<String> errorMessage = new AtomicReference<>();
            AccountAgeWitnessService accountAgeWitnessService = processModel.getAccountAgeWitnessService();
            boolean valid = accountAgeWitnessService.verifyAccountAgeWitnessAtTradeDate(trade,
                    paymentAccountPayload,
                    tradeDate,
                    pubKeyRing,
                    nonce,
                    signature,
                    errorMessage::set);
            if (!valid) {
                failed(errorMessage.get());
                return;
            }

            processModel.setBuyerPaymentAccountValidated(true);
            processModel.getTradeManager().requestPersistence();
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
