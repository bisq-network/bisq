package io.bitsquare.user;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class Arbitrator implements Serializable
{
    private static final long serialVersionUID = -2625059604136756635L;
    private String UID;
    private String pubKeyAsHex;
    private String messagePubKeyAsHex;
    private String name;
    private ID_TYPE idType;
    private List<Locale> languages;
    private Reputation reputation;
    private double maxTradeVolume;
    private double passiveServiceFee;
    private double minPassiveServiceFee;
    private double arbitrationFee;
    private double minArbitrationFee;
    private List<METHOD> arbitrationMethods;
    private List<ID_VERIFICATION> idVerifications;
    private String webUrl;
    private String description;

    public Arbitrator()
    {
    }

    public Arbitrator(String pubKeyAsHex,
                      String messagePubKeyAsHex,
                      String name,
                      ID_TYPE idType,
                      List<Locale> languages,
                      Reputation reputation,
                      double maxTradeVolume,
                      double passiveServiceFee,
                      double minPassiveServiceFee,
                      double arbitrationFee,
                      double minArbitrationFee,
                      List<METHOD> arbitrationMethods,
                      List<ID_VERIFICATION> idVerifications,
                      String webUrl,
                      String description)
    {
        this.pubKeyAsHex = pubKeyAsHex;
        this.messagePubKeyAsHex = messagePubKeyAsHex;
        this.name = name;
        this.idType = idType;
        this.languages = languages;
        this.reputation = reputation;
        this.maxTradeVolume = maxTradeVolume;
        this.passiveServiceFee = passiveServiceFee;
        this.minPassiveServiceFee = minPassiveServiceFee;
        this.arbitrationFee = arbitrationFee;
        this.minArbitrationFee = minArbitrationFee;
        this.arbitrationMethods = arbitrationMethods;
        this.idVerifications = idVerifications;
        this.webUrl = webUrl;
        this.description = description;

        //TODO for mock arbitrator
        UID = name;
    }

    public void updateFromStorage(Arbitrator savedArbitrator)
    {
        this.pubKeyAsHex = savedArbitrator.getPubKeyAsHex();
        this.messagePubKeyAsHex = savedArbitrator.getPubKeyAsHex();
        this.name = savedArbitrator.getName();
        this.idType = savedArbitrator.getIdType();
        this.languages = savedArbitrator.getLanguages();
        this.reputation = savedArbitrator.getReputation();
        this.maxTradeVolume = savedArbitrator.getMaxTradeVolume();
        this.passiveServiceFee = savedArbitrator.getPassiveServiceFee();
        this.minPassiveServiceFee = savedArbitrator.getMinPassiveServiceFee();
        this.arbitrationFee = savedArbitrator.getArbitrationFee();
        this.minArbitrationFee = savedArbitrator.getMinArbitrationFee();
        this.arbitrationMethods = savedArbitrator.getArbitrationMethods();
        this.idVerifications = savedArbitrator.getIdVerifications();
        this.webUrl = savedArbitrator.getWebUrl();
        this.description = savedArbitrator.getDescription();

        UID = pubKeyAsHex;
    }

    public int hashCode()
    {
        return Objects.hashCode(UID);
    }

    public boolean equals(Object obj)
    {
        if (!(obj instanceof Arbitrator))
            return false;
        if (obj == this)
            return true;

        Arbitrator other = (Arbitrator) obj;
        return other.getUID().equals(UID);
    }

    public String getUID()
    {
        return UID;
    }

    public String getPubKeyAsHex()
    {
        return pubKeyAsHex;
    }

    public String getMessagePubKeyAsHex()
    {
        return messagePubKeyAsHex;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getName()
    {
        return name;
    }

    public ID_TYPE getIdType()
    {
        return idType;
    }

    public List<Locale> getLanguages()
    {
        return languages;
    }

    public Reputation getReputation()
    {
        return reputation;
    }

    public double getMaxTradeVolume()
    {
        return maxTradeVolume;
    }

    public double getPassiveServiceFee()
    {
        return passiveServiceFee;
    }

    public double getMinPassiveServiceFee()
    {
        return minPassiveServiceFee;
    }

    public double getArbitrationFee()
    {
        return arbitrationFee;
    }

    public double getMinArbitrationFee()
    {
        return minArbitrationFee;
    }

    public List<METHOD> getArbitrationMethods()
    {
        return arbitrationMethods;
    }

    public List<ID_VERIFICATION> getIdVerifications()
    {
        return idVerifications;
    }

    public String getWebUrl()
    {
        return webUrl;
    }

    public String getDescription()
    {
        return description;
    }

    public enum ID_TYPE
    {
        REAL_LIFE_ID,
        NICKNAME,
        COMPANY
    }

    public enum METHOD
    {
        TLS_NOTARY,
        SKYPE_SCREEN_SHARING,
        SMART_PHONE_VIDEO_CHAT,
        REQUIRE_REAL_ID,
        BANK_STATEMENT,
        OTHER
    }

    public enum ID_VERIFICATION
    {
        PASSPORT,
        GOV_ID,
        UTILITY_BILLS,
        FACEBOOK,
        GOOGLE_PLUS,
        TWITTER,
        PGP,
        BTC_OTC,
        OTHER
    }
}
