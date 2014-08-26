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

package io.bitsquare.user;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
        id = name;
    }

    public void applyPersistedArbitrator(Arbitrator persistedArbitrator)
    {
        this.pubKeyAsHex = persistedArbitrator.getPubKeyAsHex();
        this.messagePubKeyAsHex = persistedArbitrator.getPubKeyAsHex();
        this.name = persistedArbitrator.getName();
        this.idType = persistedArbitrator.getIdType();
        this.languages = persistedArbitrator.getLanguages();
        this.reputation = persistedArbitrator.getReputation();
        this.maxTradeVolume = persistedArbitrator.getMaxTradeVolume();
        this.passiveServiceFee = persistedArbitrator.getPassiveServiceFee();
        this.minPassiveServiceFee = persistedArbitrator.getMinPassiveServiceFee();
        this.arbitrationFee = persistedArbitrator.getArbitrationFee();
        this.minArbitrationFee = persistedArbitrator.getMinArbitrationFee();
        this.arbitrationMethods = persistedArbitrator.getArbitrationMethods();
        this.idVerifications = persistedArbitrator.getIdVerifications();
        this.webUrl = persistedArbitrator.getWebUrl();
        this.description = persistedArbitrator.getDescription();

        //TODO for mock arbitrator
        id = name;
    }

    @SuppressWarnings("NonFinalFieldReferencedInHashCode")
    public int hashCode()
    {
        if (id != null)
        {
            return Objects.hashCode(id);
        }
        else
        {
            return 0;
        }
    }

    @SuppressWarnings("NonFinalFieldReferenceInEquals")
    public boolean equals(Object obj)
    {
        if (!(obj instanceof Arbitrator))
        {
            return false;
        }
        if (obj == this)
        {
            return true;
        }

        Arbitrator other = (Arbitrator) obj;
        return id != null && id.equals(other.getId());
    }


    public String getId()
    {
        return id;
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
