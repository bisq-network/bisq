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

package bisq.core.payment;

import bisq.core.payment.payload.JapanBankAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.JapanBankAccountPayload;

import org.jetbrains.annotations.NotNull;

import lombok.Getter;
import lombok.Setter;

import bisq.core.locale.Country;
import bisq.core.locale.FiatCurrency;
import bisq.core.payment.payload.JapanBankAccountPayload;

public final class JapanBankAccount extends PaymentAccount
{
    public JapanBankAccount()
    {
        super(PaymentMethod.JAPAN_BANK);
        setSingleTradeCurrency(new FiatCurrency("JPY"));
    }

    @Override
    protected PaymentAccountPayload createPayload()
    {
        return new JapanBankAccountPayload(paymentMethod.getId(), id);
    }

    // bank code
    public String getBankCode()
    {
        return ((JapanBankAccountPayload) paymentAccountPayload).getBankCode();
    }
    public void setBankCode(String bankCode)
    {
        if (bankCode == null) bankCode = "";
        ((JapanBankAccountPayload) paymentAccountPayload).setBankCode(bankCode);
    }

    // bank name
    public String getBankName()
    {
        return ((JapanBankAccountPayload) paymentAccountPayload).getBankName();
    }
    public void setBankName(String bankName)
    {
        if (bankName == null) bankName = "";
        ((JapanBankAccountPayload) paymentAccountPayload).setBankName(bankName);
    }

    // branch code
    public String getBankBranchCode()
    {
        return ((JapanBankAccountPayload) paymentAccountPayload).getBankBranchCode();
    }
    public void setBankBranchCode(String bankBranchCode)
    {
        if (bankBranchCode == null) bankBranchCode = "";
        ((JapanBankAccountPayload) paymentAccountPayload).setBankBranchCode(bankBranchCode);
    }

    // branch name
    public String getBankBranchName()
    {
        return ((JapanBankAccountPayload) paymentAccountPayload).getBankBranchName();
    }
    public void setBankBranchName(String bankBranchName)
    {
        if (bankBranchName == null) bankBranchName = "";
        ((JapanBankAccountPayload) paymentAccountPayload).setBankBranchName(bankBranchName);
    }

    // account type
    public String getBankAccountType()
    {
        return ((JapanBankAccountPayload) paymentAccountPayload).getBankAccountType();
    }
    public void setBankAccountType(String bankAccountType)
    {
        if (bankAccountType == null) bankAccountType = "";
        ((JapanBankAccountPayload) paymentAccountPayload).setBankAccountType(bankAccountType);
    }

    // account number
    public String getBankAccountNumber()
    {
        return ((JapanBankAccountPayload) paymentAccountPayload).getBankAccountNumber();
    }
    public void setBankAccountNumber(String bankAccountNumber)
    {
        if (bankAccountNumber == null) bankAccountNumber = "";
        ((JapanBankAccountPayload) paymentAccountPayload).setBankAccountNumber(bankAccountNumber);
    }

    // account name
    public String getBankAccountName()
    {
        return ((JapanBankAccountPayload) paymentAccountPayload).getBankAccountName();
    }
    public void setBankAccountName(String bankAccountName)
    {
        if (bankAccountName == null) bankAccountName = "";
        ((JapanBankAccountPayload) paymentAccountPayload).setBankAccountName(bankAccountName);
    }
}
