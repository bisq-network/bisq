/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.offerer.tasks;

import io.bitsquare.trade.handlers.ResultHandler;
import io.bitsquare.trade.protocol.trade.offerer.BuyerAcceptsOfferProtocolListener;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetupListenerForBlockChainConfirmation {
    private static final Logger log = LoggerFactory.getLogger(SetupListenerForBlockChainConfirmation.class);

    public static void run(ResultHandler resultHandler,
                           Transaction depositTransaction, BuyerAcceptsOfferProtocolListener listener) {
        log.trace("Run task");
        //TODO
        // sharedModel.offererPaymentProtocolListener.onDepositTxConfirmedInBlockchain();

        depositTransaction.getConfidence().addEventListener(new TransactionConfidence.Listener() {
            @Override
            public void onConfidenceChanged(Transaction tx, ChangeReason reason) {
                log.trace("onConfidenceChanged " + tx.getConfidence());
                if (reason == ChangeReason.TYPE &&
                        tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
                    listener.onDepositTxConfirmedInBlockchain();
                    depositTransaction.getConfidence().removeEventListener(this);
                    log.trace("Tx is in blockchain");
                    resultHandler.onResult();
                }
            }
        });
    }
}
