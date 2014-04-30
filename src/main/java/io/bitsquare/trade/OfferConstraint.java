package io.bitsquare.trade;

import io.bitsquare.bank.BankAccountType;

import java.util.List;
import java.util.Locale;

public class OfferConstraint
{
    private List<Locale> languageLocales;
    private List<BankAccountType.BankAccountTypeEnum> bankAccountTypes;

    public OfferConstraint(List<Locale> languageLocales,
                           List<BankAccountType.BankAccountTypeEnum> bankAccountTypes)
    {
        this.languageLocales = languageLocales;
        this.bankAccountTypes = bankAccountTypes;
    }

    public List<Locale> getLanguageLocales()
    {
        return languageLocales;
    }

    public List<BankAccountType.BankAccountTypeEnum> getBankAccountTypes()
    {
        return bankAccountTypes;
    }
}
