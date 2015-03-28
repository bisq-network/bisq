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

package io.bitsquare.arbitration;

import io.bitsquare.storage.Storage;

import org.bitcoinj.core.Coin;

import java.io.Serializable;

import java.security.PublicKey;

import java.util.List;
import java.util.Objects;

public class Arbitrator implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = 1L;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enums
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum ID_TYPE {
        REAL_LIFE_ID,
        NICKNAME,
        COMPANY
    }

    public enum METHOD {
        TLS_NOTARY,
        SKYPE_SCREEN_SHARING,
        SMART_PHONE_VIDEO_CHAT,
        REQUIRE_REAL_ID,
        BANK_STATEMENT,
        OTHER
    }

    public enum ID_VERIFICATION {
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


    final transient private Storage<Arbitrator> storage;

    // Persisted fields
    private final String id;
    private final byte[] pubKey;
    private final PublicKey p2pSigPubKey;
    private final String name;
    private final Reputation reputation;

    // Mutable
    private ID_TYPE idType;
    private List<String> languageCodes;
    private Coin fee;
    private List<METHOD> arbitrationMethods;
    private List<ID_VERIFICATION> idVerifications;
    private String webUrl;
    private String description;

    public Arbitrator(Storage<Arbitrator> storage,
                      String id,
                      byte[] pubKey,
                      PublicKey p2pSigPubKey,
                      String name,
                      Reputation reputation,
                      ID_TYPE idType,
                      List<String> languageCodes,
                      Coin fee,
                      List<METHOD> arbitrationMethods,
                      List<ID_VERIFICATION> idVerifications,
                      String webUrl,
                      String description) {
        this.storage = storage;
        this.id = id;
        this.pubKey = pubKey;
        this.p2pSigPubKey = p2pSigPubKey;
        this.name = name;
        this.reputation = reputation;
        this.idType = idType;
        this.languageCodes = languageCodes;
        this.fee = fee;
        this.arbitrationMethods = arbitrationMethods;
        this.idVerifications = idVerifications;
        this.webUrl = webUrl;
        this.description = description;
    }

    public void save() {
        storage.queueUpForSave();
    }

    @Override
    public int hashCode() {
        if (id != null) {
            return Objects.hashCode(id);
        }
        else {
            return 0;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Arbitrator)) {
            return false;
        }
        if (obj == this) {
            return true;
        }

        Arbitrator other = (Arbitrator) obj;
        return id != null && id.equals(other.getId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setDescription(String description) {
        this.description = description;
        save();
    }

    public void setIdType(ID_TYPE idType) {
        this.idType = idType;
        save();
    }

    public void setLanguageCodes(List<String> languageCodes) {
        this.languageCodes = languageCodes;
        save();
    }

    public void setFee(Coin fee) {
        this.fee = fee;
        save();
    }

    public void setArbitrationMethods(List<METHOD> arbitrationMethods) {
        this.arbitrationMethods = arbitrationMethods;
        save();
    }

    public void setIdVerifications(List<ID_VERIFICATION> idVerifications) {
        this.idVerifications = idVerifications;
        save();
    }

    public void setWebUrl(String webUrl) {
        this.webUrl = webUrl;
        save();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getId() {
        return id;
    }

    public byte[] getPubKey() {
        return pubKey;
    }

    public PublicKey getP2pSigPubKey() {
        return p2pSigPubKey;
    }

    public String getName() {
        return name;
    }

    public ID_TYPE getIdType() {
        return idType;
    }

    public List<String> getLanguageCodes() {
        return languageCodes;
    }

    public Reputation getReputation() {
        return reputation;
    }

    public Coin getFee() {
        return fee;
    }

    public List<METHOD> getArbitrationMethods() {
        return arbitrationMethods;
    }

    public List<ID_VERIFICATION> getIdVerifications() {
        return idVerifications;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public String getDescription() {
        return description;
    }
}
