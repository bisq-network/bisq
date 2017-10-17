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

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.payment.AccountAgeWitness;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import lombok.extern.slf4j.Slf4j;

import java.security.PublicKey;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class VerifyPeersAgeWitnessAge extends TradeTask {

    @SuppressWarnings({"WeakerAccess", "unused"})
    public VerifyPeersAgeWitnessAge(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            final PaymentAccountPayload paymentAccountPayload = checkNotNull(processModel.getTradingPeer().getPaymentAccountPayload()
                    , "Peers paymentAccountPayload must not be null");

            byte[] peersAgeWitnessInputData = null;
            AccountAgeWitness witness = null;
            byte[] peersSalt = null;
            PublicKey peersPublicKey = null;
            int nonce = 0;
            byte[] signatureOfNonce = null;

            checkArgument(processModel.getAccountAgeWitnessService().verifyAgeWitness(peersAgeWitnessInputData,
                            witness,
                            peersSalt,
                            peersPublicKey,
                            nonce,
                            signatureOfNonce), "verifyOffersAccountAgeWitness failed");
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
