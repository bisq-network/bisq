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

package bisq.core.trade.protocol.bisq_v1.tasks;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;

import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.TaskRunner;

import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class VerifyPeersAccountAgeWitness extends TradeTask {

    public VerifyPeersAccountAgeWitness(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            Offer offer = checkNotNull(trade.getOffer());
            if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
                complete();
                return;
            }

            AccountAgeWitnessService accountAgeWitnessService = processModel.getAccountAgeWitnessService();
            TradingPeer tradingPeer = processModel.getTradePeer();
            PaymentAccountPayload peersPaymentAccountPayload = checkNotNull(tradingPeer.getPaymentAccountPayload(),
                    "Peers peersPaymentAccountPayload must not be null");
            PubKeyRing peersPubKeyRing = checkNotNull(tradingPeer.getPubKeyRing(), "peersPubKeyRing must not be null");
            byte[] nonce = checkNotNull(tradingPeer.getAccountAgeWitnessNonce());
            byte[] signature = checkNotNull(tradingPeer.getAccountAgeWitnessSignature());
            AtomicReference<String> errorMsg = new AtomicReference<>();
            long currentDateAsLong = tradingPeer.getCurrentDate();
            // In case the peer has an older version we get 0, so we use our time instead
            Date peersCurrentDate = currentDateAsLong > 0 ? new Date(currentDateAsLong) : new Date();
            boolean isValid = accountAgeWitnessService.verifyAccountAgeWitness(trade,
                    peersPaymentAccountPayload,
                    peersCurrentDate,
                    peersPubKeyRing,
                    nonce,
                    signature,
                    errorMsg::set);
            if (isValid) {
                complete();
            } else {
                failed(errorMsg.get());
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
