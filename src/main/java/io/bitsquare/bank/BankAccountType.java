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

package io.bitsquare.bank;

import java.util.ArrayList;
import java.util.Arrays;

public enum BankAccountType {
    SEPA("IBAN", "BIC"),
    WIRE("primary ID", "secondary ID"),
    INTERNATIONAL("primary ID", "secondary ID"),
    OK_PAY("primary ID", "secondary ID"),
    NET_TELLER("primary ID", "secondary ID"),
    PERFECT_MONEY("primary ID", "secondary ID");

    private final String primaryId;
    private final String secondaryId;

    BankAccountType(String primaryId, String secondaryId) {
        this.primaryId = primaryId;
        this.secondaryId = secondaryId;
    }

    public static ArrayList<BankAccountType> getAllBankAccountTypes() {
        return new ArrayList<>(Arrays.asList(BankAccountType.values()));
    }

    public String getPrimaryId() {
        return primaryId;
    }

    public String getSecondaryId() {
        return secondaryId;
    }
}
