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

package io.bitsquare.trade.protocol.taker;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.trade.handlers.ExceptionHandler;
import io.bitsquare.trade.handlers.ResultHandler;
import io.bitsquare.trade.protocol.shared.VerifyPeerAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyOffererAccount {
    private static final Logger log = LoggerFactory.getLogger(VerifyOffererAccount.class);

    public static void run(ResultHandler resultHandler, ExceptionHandler exceptionHandler, BlockChainFacade blockChainFacade, String peersAccountId, BankAccount peersBankAccount) {
        log.trace("Run task");
        VerifyPeerAccount.run(resultHandler, exceptionHandler, blockChainFacade, peersAccountId, peersBankAccount);
    }
}
