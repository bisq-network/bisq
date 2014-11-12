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

package io.bitsquare.trade.protocol.trade.shared.tasks;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.util.task.ExceptionHandler;
import io.bitsquare.util.task.ResultHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyPeerAccount {
    private static final Logger log = LoggerFactory.getLogger(VerifyPeerAccount.class);

    public static void run(ResultHandler resultHandler, ExceptionHandler exceptionHandler,
                           BlockChainService blockChainService, String peersAccountId, BankAccount peersBankAccount) {
        //TODO mocked yet
        if (blockChainService.verifyAccountRegistration()) {
            if (blockChainService.isAccountBlackListed(peersAccountId, peersBankAccount)) {
                log.error("Taker is blacklisted");
                exceptionHandler.handleException(new Exception("Taker is blacklisted"));
            }
            else {
                resultHandler.handleResult();
            }
        }
        else {
            log.error("Account registration validation for peer faultHandler.onFault.");
            exceptionHandler.handleException(new Exception("Account registration validation for peer faultHandler" +
                    ".onFault."));
        }
    }

}
