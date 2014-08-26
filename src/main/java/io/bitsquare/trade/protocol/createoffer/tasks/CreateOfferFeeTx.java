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

package io.bitsquare.trade.protocol.createoffer.tasks;

import com.google.bitcoin.core.InsufficientMoneyException;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.trade.handlers.FaultHandler;
import io.bitsquare.trade.handlers.TransactionResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateOfferFeeTx {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferFeeTx.class);

    public static void run(TransactionResultHandler resultHandler, FaultHandler faultHandler, WalletFacade walletFacade, String offerId) {
        try {
            resultHandler.onResult(walletFacade.createOfferFeeTx(offerId));
        } catch (InsufficientMoneyException e) {
            faultHandler.onFault("Offer fee payment failed because there is insufficient money in the trade pocket. ", e);
        } catch (Throwable t) {
            faultHandler.onFault("Offer fee payment failed because of an exception occurred. ", t);
        }
    }
}
