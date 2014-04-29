package io.bitsquare.trade;

import io.bitsquare.bank.BankAccountType;

import java.util.List;
import java.util.Locale;

public class OfferConstraints
{
    //TODO remove
    private double collateral;
    private String arbitrator;
    private String identityVerification;

    private List<Locale> countryLocales;
    private List<Locale> languageLocales;
    private List<BankAccountType.BankAccountTypeEnum> bankAccountTypes;

    public OfferConstraints(List<Locale> countryLocales,
                            List<Locale> languageLocales,
                            double collateral,
                            List<BankAccountType.BankAccountTypeEnum> bankAccountTypes,
                            String arbitrator,
                            String identityVerification)
    {
        this.countryLocales = countryLocales;
        this.languageLocales = languageLocales;
        this.bankAccountTypes = bankAccountTypes;


        this.collateral = collateral;
        this.arbitrator = arbitrator;
        this.identityVerification = identityVerification;
    }


    /**
     * @return List of ISO3 country codes
     */
    public List<Locale> getCountryLocales()
    {
        return countryLocales;
    }

    /**
     * @return List of ISO3 language codes
     */
    public List<Locale> getLanguageLocales()
    {
        return languageLocales;
    }

    public List<BankAccountType.BankAccountTypeEnum> getBankAccountTypes()
    {
        return bankAccountTypes;
    }


    public String getArbitrator()
    {
        return arbitrator;
    }

    public String getIdentityVerification()
    {
        return identityVerification;
    }

    public double getCollateral()
    {
        return collateral;
    }
}
