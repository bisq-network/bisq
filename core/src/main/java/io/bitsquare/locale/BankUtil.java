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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BankUtil {

    private static final Logger log = LoggerFactory.getLogger(BankUtil.class);

    // BankName
    public static boolean isBankNameRequired(String countryCode) {
        switch (countryCode) {
            case "GB":
            case "US":
            case "NZ":
            case "AU":
            case "CA":
            case "SE":
            case "HK":
                return false;
            case "MX":
            case "BR":
                return true;
            default:
                return true;
        }
    }

    public static String getBankNameLabel(String countryCode) {
        switch (countryCode) {
            case "BR":
                return "Bank name:";
            default:
                return isBankNameRequired(countryCode) ? "Bank name:" : "Bank name (optional):";
        }
    }

    // BankId
    public static boolean isBankIdRequired(String countryCode) {
        switch (countryCode) {
            case "GB":
            case "US":
            case "BR":
            case "NZ":
            case "AU":
            case "SE":
                return false;
            case "CA":
            case "MX":
            case "HK":
                return true;
            default:
                return true;
        }
    }

    public static String getBankIdLabel(String countryCode) {
        switch (countryCode) {
            case "CA":
                return "Institution Number:";
            case "MX":
            case "HK":
                return "Bank code:";
            default:
                return isBankIdRequired(countryCode) ? "Bank ID (BIC/SWIFT):" : "Bank ID (BIC/SWIFT) (optional):";
        }

    }

    // BranchId
    public static boolean isBranchIdRequired(String countryCode) {
        switch (countryCode) {
            case "GB":
            case "US":
            case "BR":
            case "AU":
            case "CA":
                return true;
            case "NZ":
            case "MX":
            case "HK":
            case "SE":
                return false;
            default:
                return true;
        }
    }

    public static String getBranchIdLabel(String countryCode) {
        switch (countryCode) {
            case "GB":
                return "UK sort code:";
            case "US":
                return "Routing Number:";
            case "BR":
                return "Branch code:";
            case "AU":
                return "BSB code:";
            case "CA":
                return "Transit Number:";
            default:
                return isBranchIdRequired(countryCode) ? "Branch no.:" : "Branch no. (optional):";
        }
    }


    // AccountNr
    public static boolean isAccountNrRequired(String countryCode) {
        switch (countryCode) {
            default:
                return true;
        }
    }

    public static String getAccountNrLabel(String countryCode) {
        switch (countryCode) {
            case "GB":
            case "US":
            case "BR":
            case "NZ":
            case "AU":
            case "CA":
            case "HK":
                return "Account number:";
            case "SE":
                return "Bankgiro number:";
            case "MX":
                return "CLABE:";
            default:
                return "Account no. (IBAN):";
        }
    }

    // AccountType
    public static boolean isAccountTypeRequired(String countryCode) {
        switch (countryCode) {
            case "US":
            case "BR":
            case "CA":
                return true;
            default:
                return false;
        }
    }

    public static String getAccountTypeLabel(String countryCode) {
        switch (countryCode) {
            case "US":
            case "BR":
            case "CA":
                return "Account type:";
            default:
                return "";
        }
    }

    public static List<String> getAccountTypeValues(String countryCode) {
        switch (countryCode) {
            case "US":
            case "BR":
            case "CA":
                return Arrays.asList("Checking", "Savings");
            default:
                return new ArrayList<>();
        }
    }


    // HolderId
    public static boolean isHolderIdRequired(String countryCode) {
        switch (countryCode) {
            case "BR":
            case "CL":
                return true;
            default:
                return false;
        }
    }

    public static String getHolderIdLabel(String countryCode) {
        switch (countryCode) {
            case "BR":
                return "Tax Registration Number (CPF):";
            case "CL":
                return "RUT Number:";
            default:
                return "Personal ID:";
        }
    }

    // Validation
    public static boolean useValidation(String countryCode) {
        switch (countryCode) {
            case "GB":
            case "US":
            case "BR":
            case "AU":
            case "CA":
            case "NZ":
            case "MX":
            case "HK":
            case "SE":
                return true;
            default:
                return false;
        }
    }
}
