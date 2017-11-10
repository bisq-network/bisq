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

package io.bisq.core.payment.payload;

import io.bisq.common.crypto.CryptoUtils;
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.common.util.JsonExclude;
import io.bisq.common.util.Utilities;
import io.bisq.consensus.RestrictedByContractJson;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// That class is used in the contract for creating the contract json. Any change will break the contract.
// If a field gets added it need to be be annotated with @JsonExclude (excluded from contract).
// We should add an extraDataMap as in StoragePayload objects

@Getter
@EqualsAndHashCode
@ToString
@Slf4j
public abstract class PaymentAccountPayload implements NetworkPayload, RestrictedByContractJson {

    // Keys for excludeFromJsonDataMap
    public static final String SALT = "salt";

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
    @Nullable
    protected final Map<String, String> excludeFromJsonDataMap;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    PaymentAccountPayload(String paymentMethodId, String id) {
        this(paymentMethodId,
                id,
                -1,
                null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected PaymentAccountPayload(String paymentMethodId,
                                    String id,
                                    long maxTradePeriod,
                                    @Nullable Map<String, String> excludeFromJsonDataMapParam) {
        this.paymentMethodId = paymentMethodId;
        this.id = id;
        this.maxTradePeriod = maxTradePeriod;
        this.excludeFromJsonDataMap = excludeFromJsonDataMapParam == null ? new HashMap<>() : excludeFromJsonDataMapParam;

        // If not set (old versions) we set by default a random 256 bit salt.
        // User can set salt as well by hex string.
        // Persisted value will overwrite that
        if (!this.excludeFromJsonDataMap.containsKey(SALT))
            this.excludeFromJsonDataMap.put(SALT, Utilities.encodeToHex(CryptoUtils.getRandomBytes(32)));
    }

    protected PB.PaymentAccountPayload.Builder getPaymentAccountPayloadBuilder() {
        final PB.PaymentAccountPayload.Builder builder = PB.PaymentAccountPayload.newBuilder()
                .setPaymentMethodId(paymentMethodId)
                .setMaxTradePeriod(maxTradePeriod)
                .setId(id);

        Optional.ofNullable(excludeFromJsonDataMap).ifPresent(builder::putAllExcludeFromJsonData);

        return builder;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract public String getPaymentDetails();

    abstract public String getPaymentDetailsForTradePopup();

    public byte[] getSalt() {
        checkNotNull(excludeFromJsonDataMap, "excludeFromJsonDataMap must not be null");
        checkArgument(excludeFromJsonDataMap.containsKey(SALT), "Salt must have been set in excludeFromJsonDataMap.");
        return Utilities.decodeFromHex(excludeFromJsonDataMap.get(SALT));
    }

    public void setSalt(byte[] salt) {
        checkNotNull(excludeFromJsonDataMap, "excludeFromJsonDataMap must not be null");
        excludeFromJsonDataMap.put(SALT, Utilities.encodeToHex(salt));
    }

    // Identifying data of payment account (e.g. IBAN).
    // This is critical code for verifying age of payment account.
    // Any change would break validation of historical data!
    public abstract byte[] getAgeWitnessInputData();

    protected byte[] getAgeWitnessInputData(byte[] data) {
        return ArrayUtils.addAll(paymentMethodId.getBytes(Charset.forName("UTF-8")), data);
    }
}
