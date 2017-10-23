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
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.offer.Offer;
import io.bisq.core.payment.AccountAgeWitness;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.TradingPeer;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Optional;

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

            final AccountAgeWitnessService accountAgeWitnessService = processModel.getAccountAgeWitnessService();
            final TradingPeer tradingPeer = processModel.getTradingPeer();
            final PaymentAccountPayload peersPaymentAccountPayload = checkNotNull(tradingPeer.getPaymentAccountPayload(),
                "Peers peersPaymentAccountPayload must not be null");
            final PubKeyRing peersPubKeyRing = checkNotNull(tradingPeer.getPubKeyRing(), "peersPubKeyRing must not be null");
            final Offer offer = trade.getOffer();
            final Optional<String> accountAgeWitnessHashAsHex = offer.getAccountAgeWitnessHashAsHex();
            Optional<AccountAgeWitness> witnessOptional = accountAgeWitnessHashAsHex.isPresent() ?
                accountAgeWitnessService.getPeersWitnessByHashAsHex(accountAgeWitnessHashAsHex.get())
                : Optional.<AccountAgeWitness>empty();
            byte[] nonce = tradingPeer.getAccountAgeWitnessNonce();
            byte[] signatureOfNonce = tradingPeer.getAccountAgeWitnessSignatureOfNonce();
            if (witnessOptional.isPresent() && nonce != null && signatureOfNonce != null) {
                AccountAgeWitness witness = witnessOptional.get();
                final String[] errorMsg = new String[1];
                byte[] peersSignatureOfAccountHash = tradingPeer.getAccountAgeWitnessSignatureOfAccountData();
                long currentDateAsLong = tradingPeer.getCurrentDate();
                // In case the peer has an older version we get 0, so we use our time instead
                final Date peersCurrentDate = currentDateAsLong > 0 ? new Date(currentDateAsLong) : new Date();
                boolean result = accountAgeWitnessService.verifyPeersAccountAgeWitness(offer,
                    peersPaymentAccountPayload,
                    peersCurrentDate,
                    witness,
                    peersPubKeyRing,
                    peersSignatureOfAccountHash,
                    nonce,
                    signatureOfNonce,
                    errorMessage -> errorMsg[0] = errorMessage);
                if (result)
                    complete();
                else
                    failed(errorMsg[0]);
            } else {
                String msg = !witnessOptional.isPresent() ?
                    "Peers AccountAgeWitness is not found." :
                    "Peer seems to uses a pre v0.6 application which does not support sending of account age witness verification nonce and signature.";
                msg += "\nTrade ID=" + trade.getId();
                if (new Date().after(new GregorianCalendar(2018, GregorianCalendar.FEBRUARY, 1).getTime())) {
                    msg = "The account age witness verification failed.\nReason: " + msg;
                    log.error(msg);
                    failed(msg);
                } else {
                    log.warn(msg + "\nWe tolerate offers without account age witness until first of Feb. 2018");
                    complete();
                }
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
