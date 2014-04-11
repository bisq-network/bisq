package io.bitsquare.trade;

import java.util.List;

public class OfferConstraints
{
    private double collateral;
    private List<String> countries;
    private List<String> languages;
    private List<String> bankTransferTypes;
    private String arbitrator;
    private String identityVerification;

    public OfferConstraints(List<String> countries,
                            List<String> languages,
                            double collateral,
                            List<String> bankTransferTypes,
                            String arbitrator,
                            String identityVerification)
    {
        this.countries = countries;
        this.languages = languages;
        this.collateral = collateral;
        this.bankTransferTypes = bankTransferTypes;
        this.arbitrator = arbitrator;
        this.identityVerification = identityVerification;
    }

    public double getCollateral()
    {
        return collateral;
    }

    public List<String> getCountries()
    {
        return countries;
    }

    public List<String> getLanguages()
    {
        return languages;
    }

    public List<String> getBankTransferTypes()
    {
        return bankTransferTypes;
    }

    public String getArbitrator()
    {
        return arbitrator;
    }

    public String getIdentityVerification()
    {
        return identityVerification;
    }
}
