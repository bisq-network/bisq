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

package io.bitsquare.btc;

import io.bitsquare.bank.BankAccount;

import javax.inject.Inject;

/**
 * A service delivers blockchain functionality from the BitcoinJ library.
 */
@SuppressWarnings({"UnusedDeclaration", "UnusedParameters"})
public class BlockChainService {
    @Inject
    public BlockChainService() {

    }

    //TODO
    @SuppressWarnings("SameReturnValue")
    public boolean isAccountBlackListed(String accountId, BankAccount bankAccount) {
        return false;
    }

    //TODO
    @SuppressWarnings("SameReturnValue")
    public boolean verifyAccountRegistration() {
        return true;
    }

    @SuppressWarnings("SameReturnValue")
    private boolean findAddressInBlockChain(String address) {
        // TODO lookup for address in blockchain
        return true;
    }


    @SuppressWarnings("SameReturnValue")
    private byte[] getDataForTxWithAddress(String address) {
        // TODO return data after OP_RETURN
        return null;
    }

    @SuppressWarnings("SameReturnValue")
    private boolean isFeePayed(String address) {
        // TODO check if fee is paid
        return true;
    }

    @SuppressWarnings("SameReturnValue")
    private boolean isAccountIDBlacklisted(String accountID) {
        // TODO check if accountID is on blacklist
        return false;
    }

    @SuppressWarnings("SameReturnValue")
    private boolean isBankAccountBlacklisted(BankAccount bankAccount) {
        // TODO check if accountID is on blacklist
        return false;
    }
}
