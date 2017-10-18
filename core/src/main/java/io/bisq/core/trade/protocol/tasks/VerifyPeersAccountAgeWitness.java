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
import io.bisq.core.payment.AccountAgeWitness;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.trade.Trade;
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
            final PaymentAccountPayload peersPaymentAccountPayload = checkNotNull(processModel.getTradingPeer().getPaymentAccountPayload(),
                    "Peers peersPaymentAccountPayload must not be null");

            final String[] errorMsg1 = new String[1];
            boolean result = accountAgeWitnessService.verifyTradeLimit(trade.getOffer(), peersPaymentAccountPayload, errorMessage -> errorMsg1[0] = errorMessage);
            if (result) {
                byte[] nonce = processModel.getTradingPeer().getAccountAgeWitnessNonce();
                byte[] signatureOfNonce = processModel.getTradingPeer().getAccountAgeWitnessSignatureOfNonce();
                Optional<AccountAgeWitness> witnessOptional = accountAgeWitnessService.getWitnessByPaymentAccountPayload(peersPaymentAccountPayload);
                if (witnessOptional.isPresent() && nonce != null && signatureOfNonce != null) {
                    AccountAgeWitness witness = witnessOptional.get();
                    final PubKeyRing pubKeyRing = processModel.getTradingPeer().getPubKeyRing();
                    checkNotNull(pubKeyRing, "processModel.getTradingPeer().getPubKeyRing() must not be null");
                    final String[] errorMsg2 = new String[1];
                    result = accountAgeWitnessService.verifyAccountAgeWitness(peersPaymentAccountPayload.getAgeWitnessInputData(),
                            witness,
                            peersPaymentAccountPayload.getSalt(),
                            pubKeyRing.getSignaturePubKey(),
                            nonce,
                            signatureOfNonce,
                            errorMessage -> errorMsg2[0] = errorMessage);
                    if (result)
                        complete();
                    else
                        failed(errorMsg2[0]);
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
                        log.warn(msg + "\nWe tolerate that until 1. of Feb. 2018");
                        complete();
                    }
                }
            } else {
                String msg = "The offer verification failed.\nReason: " + errorMsg1[0];
                log.error(msg);
                failed(msg);
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
