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

package io.bitsquare.fiat;

import io.bitsquare.locale.Country;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

@Immutable
public class FiatAccount implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = 1L;

    public enum Type {
        IRC("", ""),
        SEPA("IBAN", "BIC"),
        WIRE("primary ID", "secondary ID"),
        INTERNATIONAL("primary ID", "secondary ID"),
        OK_PAY("primary ID", "secondary ID"),
        NET_TELLER("primary ID", "secondary ID"),
        PERFECT_MONEY("primary ID", "secondary ID");

        public final String primaryId;
        public final String secondaryId;

        Type(String primaryId, String secondaryId) {
            this.primaryId = primaryId;
            this.secondaryId = secondaryId;
        }

        public static ArrayList<Type> getAllBankAccountTypes() {
            return new ArrayList<>(Arrays.asList(Type.values()));
        }
    }

    public final String id;
    public final String nameOfBank;
    public final Type type;
    public final Country country;     // where bank is registered
    public final String accountPrimaryID;  // like IBAN
    public final String accountSecondaryID; // like BIC
    public final String accountHolderName;


    // The main currency if account support multiple currencies.
    // The user can create multiple bank accounts with same bank account but other currency if his bank account
    // support that.
    public final String currencyCode;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public FiatAccount(Type type, String currencyCode, Country country, String nameOfBank,
                       String accountHolderName, String accountPrimaryID, String accountSecondaryID) {
        this.type = type;
        this.currencyCode = currencyCode;
        this.country = country;
        this.nameOfBank = nameOfBank;
        this.accountHolderName = accountHolderName;
        this.accountPrimaryID = accountPrimaryID;
        this.accountSecondaryID = accountSecondaryID;

        id = nameOfBank;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FiatAccount)) return false;
        if (obj == this) return true;

        final FiatAccount other = (FiatAccount) obj;
        return id.equals(other.id);
    }

    @Override
    public String toString() {
        return "FiatAccount{" +
                "id='" + id + '\'' +
                ", nameOfBank='" + nameOfBank + '\'' +
                ", type=" + type +
                ", country=" + country +
                ", accountPrimaryID='" + accountPrimaryID + '\'' +
                ", accountSecondaryID='" + accountSecondaryID + '\'' +
                ", accountHolderName='" + accountHolderName + '\'' +
                ", currencyCode='" + currencyCode + '\'' +
                '}';
    }
}
