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

package io.bitsquare.locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BankUtil {
    private static final Logger log = LoggerFactory.getLogger(BankUtil.class);


    //TODO set country specific labels
    public static String getBankIdLabel(String countryCode) {
        if (countryCode == null)
            countryCode = "";
        switch (countryCode) {
            case "GB":
                return "Bank nr. or BIC/SWIFT:";
            default:
                return "Bank nr. or BIC/SWIFT:";
        }

    }

    //TODO set country specific labels
    public static String getBranchIdLabel(String countryCode) {
        if (countryCode == null)
            countryCode = "";
        switch (countryCode) {
            case "US":
                return "Routing Number:";
            case "GB":
                return "UK Sort code:";
            case "CA":
                return "Transit Number:";
            default:
                return "Branch nr. (optional):";
        }
    }

    //TODO set country specific labels
    public static String getAccountNrLabel(String countryCode) {
        if (countryCode == null)
            countryCode = "";
        switch (countryCode) {
            case "GB":
                return "Account number";
            default:
                return "Account nr. or IBAN:";
        }
    }

    public static String getHolderIdLabel(String countryCode) {
        if (countryCode == null)
            countryCode = "";
        switch (countryCode) {
            case "BR":
                return "CPF Number:";
            case "CL":
                return "RUT Number:";
            default:
                return "Personal ID:";
        }
    }

    public static boolean isBankNameRequired(String countryCode) {
        if (countryCode == null)
            countryCode = "";
        switch (countryCode) {
            case "GB":
                return false;
            default:
                return true;
        }
    }

    public static boolean isBankIdRequired(String countryCode) {
        if (countryCode == null)
            countryCode = "";
        switch (countryCode) {
            case "GB":
                return false;
            default:
                return true;
        }
    }

    public static boolean isBranchIdRequired(String countryCode) {
        if (countryCode == null)
            countryCode = "";
        switch (countryCode) {
            case "GB":
                return true;
            default:
                return true;
        }
    }

    public static boolean isAccountNrRequired(String countryCode) {
        if (countryCode == null)
            countryCode = "";
        switch (countryCode) {
            default:
                return true;
        }
    }

    public static boolean isHolderIdRequired(String countryCode) {
        if (countryCode == null)
            countryCode = "";
        switch (countryCode) {
            case "BR":
            case "CL":
                return true;
            default:
                return false;
        }
    }
}
