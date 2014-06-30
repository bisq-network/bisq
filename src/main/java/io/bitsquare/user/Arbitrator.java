package io.bitsquare.user;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Arbitrator implements Serializable
{
    private static final long serialVersionUID = -2625059604136756635L;

    private String id;
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
    @Nullable
    private String webUrl;
    @Nullable
    private String description;

    public Arbitrator()
    {
    }

    public Arbitrator(@NotNull String pubKeyAsHex,
                      @NotNull String messagePubKeyAsHex,
                      @NotNull String name,
                      @NotNull ID_TYPE idType,
                      @NotNull List<Locale> languages,
                      @NotNull Reputation reputation,
                      double maxTradeVolume,
                      double passiveServiceFee,
                      double minPassiveServiceFee,
                      double arbitrationFee,
                      double minArbitrationFee,
                      @NotNull List<METHOD> arbitrationMethods,
                      @NotNull List<ID_VERIFICATION> idVerifications,
                      @Nullable String webUrl,
                      @Nullable String description)
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
        id = name;
    }

    public void updateFromStorage(@NotNull Arbitrator savedArbitrator)
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

        //TODO for mock arbitrator
        id = name;
    }

    @SuppressWarnings("NonFinalFieldReferencedInHashCode")
    public int hashCode()
    {
        if (id != null)
            return Objects.hashCode(id);
        else
            return 0;
    }

    @SuppressWarnings("NonFinalFieldReferenceInEquals")
    public boolean equals(@Nullable Object obj)
    {
        if (!(obj instanceof Arbitrator))
            return false;
        if (obj == this)
            return true;

        Arbitrator other = (Arbitrator) obj;
        return id != null && id.equals(other.getId());
    }

    @NotNull
    public String getId()
    {
        return id;
    }

    @NotNull
    public String getPubKeyAsHex()
    {
        return pubKeyAsHex;
    }

    @NotNull
    public String getMessagePubKeyAsHex()
    {
        return messagePubKeyAsHex;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @NotNull
    public String getName()
    {
        return name;
    }

    @NotNull
    public ID_TYPE getIdType()
    {
        return idType;
    }

    @NotNull
    public List<Locale> getLanguages()
    {
        return languages;
    }

    @NotNull
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

    @NotNull
    public List<METHOD> getArbitrationMethods()
    {
        return arbitrationMethods;
    }

    @NotNull
    public List<ID_VERIFICATION> getIdVerifications()
    {
        return idVerifications;
    }

    @Nullable
    public String getWebUrl()
    {
        return webUrl;
    }

    @Nullable
    public String getDescription()
    {
        return description;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enums
    ///////////////////////////////////////////////////////////////////////////////////////////

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
