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

package bisq.core.payment.payload;

import bisq.core.locale.Res;

import bisq.common.consensus.UsedForTradeContractJson;
import bisq.common.crypto.CryptoUtils;
import bisq.common.crypto.Hash;
import bisq.common.encoding.canonical.Canonical;
import bisq.common.encoding.canonical.CanonicalEncoder;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.util.JsonExclude;
import bisq.common.util.Utilities;

import com.google.common.base.Strings;

import org.apache.commons.lang3.ArrayUtils;

import java.nio.charset.StandardCharsets;


import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import static bisq.core.payment.payload.PaymentAccountPayloadExcludeFromJsonMap.Keys.*;
import static com.google.common.base.Preconditions.checkArgument;

// That class is used in the contract for creating the contract json. Any change will break the contract.
// If a field gets added it need to be be annotated with @JsonExclude (excluded from contract).
// We should add an extraDataMap as in StoragePayload objects

@Getter
@EqualsAndHashCode
@ToString
@Slf4j
public abstract class PaymentAccountPayload implements NetworkPayload, UsedForTradeContractJson, Canonical {
    protected final String paymentMethodId;
    protected final String id;

    // Is just kept for not breaking backward compatibility. Set to -1 to indicate it is no used anymore.
    protected final long maxTradePeriod;

    // In v0.6 we removed maxTradePeriod but we need to keep it in the PB file for backward compatibility
    // protected final long maxTradePeriod;

    // Used for new data (e.g. salt introduced in v0.6) which would break backward compatibility as
    // PaymentAccountPayload is used for the json contract and a trade with a user who has an older version would
    // fail the contract verification.
    @JsonExclude
    protected final PaymentAccountPayloadExcludeFromJsonMap excludeFromJsonDataMap;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    PaymentAccountPayload(String paymentMethodId, String id) {
        this(paymentMethodId,
                id,
                -1,
                new PaymentAccountPayloadExcludeFromJsonMap());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected PaymentAccountPayload(String paymentMethodId,
                                    String id,
                                    long maxTradePeriod,
                                    PaymentAccountPayloadExcludeFromJsonMap excludeFromJsonDataMapParam) {
        this.paymentMethodId = paymentMethodId;
        this.id = id;
        this.maxTradePeriod = maxTradePeriod;
        this.excludeFromJsonDataMap = excludeFromJsonDataMapParam;

        // If not set (old versions) we set by default a random 256 bit salt.
        // User can set salt as well by hex string.
        // Persisted value will overwrite that
        if (!this.excludeFromJsonDataMap.containsKey(SALT))
            this.excludeFromJsonDataMap.put(SALT, Utilities.encodeToHex(CryptoUtils.getRandomBytes(32)));
    }

    protected protobuf.PaymentAccountPayload.Builder getPaymentAccountPayloadBuilder() {
        @SuppressWarnings("deprecation")
        final protobuf.PaymentAccountPayload.Builder builder = protobuf.PaymentAccountPayload.newBuilder()
                .setPaymentMethodId(paymentMethodId)
                .setMaxTradePeriod(maxTradePeriod)
                .setId(id);

        builder.putAllExcludeFromJsonData(excludeFromJsonDataMap.getMap());

        return builder;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public abstract String getPaymentDetails();

    public abstract String getPaymentDetailsForTradePopup();

    public boolean showRefTextWarning() {
        return true;
    }

    public byte[] getSalt() {
        checkArgument(excludeFromJsonDataMap.containsKey(SALT), "Salt must have been set in excludeFromJsonDataMap.");
        return Utilities.decodeFromHex(excludeFromJsonDataMap.get(SALT));
    }

    public void setSalt(byte[] salt) {
        excludeFromJsonDataMap.put(SALT, Utilities.encodeToHex(salt));
    }

    public String getHolderName() {
        return excludeFromJsonDataMap.getOrDefault(HOLDER_NAME, "");
    }

    public String getHolderNameOrPromptIfEmpty() {
        return getHolderName().isEmpty() ? Res.get("payment.account.owner.ask") : getHolderName();
    }

    public void setHolderName(String holderName) {
        // an empty string must result in the mapping removing the entry.
        excludeFromJsonDataMap.compute(HOLDER_NAME,
                (k, v) -> Strings.emptyToNull(holderName));
    }

    // Identifying data of payment account (e.g. IBAN).
    // This is critical code for verifying age of payment account.
    // Any change would break validation of historical data!
    public abstract byte[] getAgeWitnessInputData();

    protected byte[] getAgeWitnessInputData(byte[] data) {
        return ArrayUtils.addAll(paymentMethodId.getBytes(StandardCharsets.UTF_8), data);
    }

    public String getOwnerId() {
        return null;
    }

    public final byte[] getHashForContract() {
        return Hash.getRipemd160hash(encodeCanonical());
    }

    @Override
    public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
        return PaymentAccountPayloadCanonicalSchemas.encode(this, canonicalEncoder);
    }
}
