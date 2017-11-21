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

package io.bisq.core.trade.protocol.tasks;

import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.TradingPeer;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class VerifyPeersAccountAgeWitness extends TradeTask {

    @SuppressWarnings({"WeakerAccess", "unused"})
    public VerifyPeersAccountAgeWitness(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            if (CurrencyUtil.isFiatCurrency(trade.getOffer().getCurrencyCode())) {
                final AccountAgeWitnessService accountAgeWitnessService = processModel.getAccountAgeWitnessService();
                final TradingPeer tradingPeer = processModel.getTradingPeer();
                final PaymentAccountPayload peersPaymentAccountPayload = checkNotNull(tradingPeer.getPaymentAccountPayload(),
                    "Peers peersPaymentAccountPayload must not be null");
                final PubKeyRing peersPubKeyRing = checkNotNull(tradingPeer.getPubKeyRing(), "peersPubKeyRing must not be null");
                byte[] nonce = tradingPeer.getAccountAgeWitnessNonce();
                byte[] signature = tradingPeer.getAccountAgeWitnessSignature();
                if (nonce != null && signature != null) {
                    final String[] errorMsg = new String[1];
                    long currentDateAsLong = tradingPeer.getCurrentDate();
                    // In case the peer has an older version we get 0, so we use our time instead
                    final Date peersCurrentDate = currentDateAsLong > 0 ? new Date(currentDateAsLong) : new Date();
                    boolean result = accountAgeWitnessService.verifyAccountAgeWitness(trade,
                        peersPaymentAccountPayload,
                        peersCurrentDate,
                        peersPubKeyRing,
                        nonce,
                        signature,
                        errorMessage -> errorMsg[0] = errorMessage);
                    if (result)
                        complete();
                    else
                        failed(errorMsg[0]);
                } else {
                    String msg = "Seems that offer was created with an application before v0.6 which did not support the account age witness verification.";
                    msg += "\nTrade ID=" + trade.getId();
                    if (new Date().after(AccountAgeWitnessService.FULL_ACTIVATION)) {
                        msg = "The account age witness verification failed.\nReason: " + msg + "\nAfter first of Feb. 2018 we don't support old offers without account age witness verification anymore.";
                        log.error(msg);
                        failed(msg);
                    } else {
                        log.warn(msg + "\nWe tolerate offers without account age witness until first of Feb. 2018");
                        complete();
                    }
                }
            } else {
                complete();
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
