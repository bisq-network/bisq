/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.filter;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.bisq.common.app.DevEnv;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.core.user.User;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.HashMapChangedListener;
import io.bisq.network.p2p.storage.P2PService;
import io.bisq.protobuffer.crypto.KeyRing;
import io.bisq.protobuffer.payload.filter.Filter;
import io.bisq.protobuffer.payload.filter.PaymentAccountFilter;
import io.bisq.protobuffer.payload.p2p.storage.ProtectedStorageEntry;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.security.SignatureException;
import java.util.stream.Collectors;

import static org.bitcoinj.core.Utils.HEX;

public class FilterManager {
    private static final Logger log = LoggerFactory.getLogger(FilterManager.class);

    private final P2PService p2PService;
    private final KeyRing keyRing;
    private final User user;
    private final ObjectProperty<Filter> filterProperty = new SimpleObjectProperty<>();

    private static final String pubKeyAsHex = DevEnv.USE_DEV_PRIVILEGE_KEYS ?
            DevEnv.DEV_PRIVILEGE_PUB_KEY :
            "022ac7b7766b0aedff82962522c2c14fb8d1961dabef6e5cfd10edc679456a32f1";
    private ECKey filterSigningKey;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FilterManager(P2PService p2PService, KeyRing keyRing, User user,
                         @Named(AppOptionKeys.IGNORE_DEV_MSG_KEY) boolean ignoreDevMsg) {
        this.p2PService = p2PService;
        this.keyRing = keyRing;
        this.user = user;

        if (!ignoreDevMsg) {
            p2PService.addHashSetChangedListener(new HashMapChangedListener() {
                @Override
                public void onAdded(ProtectedStorageEntry data) {
                    if (data.getStoragePayload() instanceof Filter) {
                        Filter filter = (Filter) data.getStoragePayload();
                        if (verifySignature(filter))
                            filterProperty.set(filter);
                    }
                }

                @Override
                public void onRemoved(ProtectedStorageEntry data) {
                    if (data.getStoragePayload() instanceof Filter) {
                        Filter filter = (Filter) data.getStoragePayload();
                        if (verifySignature(filter))
                            filterProperty.set(null);
                    }
                }
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ReadOnlyObjectProperty<Filter> filterProperty() {
        return filterProperty;
    }

    public Filter getFilter() {
        return filterProperty.get();
    }

    public boolean addFilterMessageIfKeyIsValid(Filter filter, String privKeyString) {
        // if there is a previous message we remove that first
        if (user.getDevelopersFilter() != null)
            removeFilterMessageIfKeyIsValid(privKeyString);

        boolean isKeyValid = isKeyValid(privKeyString);
        if (isKeyValid) {
            signAndAddSignatureToFilter(filter);
            user.setDevelopersFilter(filter);

            boolean result = p2PService.addData(filter, true);
            if (result)
                log.trace("Add filter to network was successful. FilterMessage = " + filter);

        }
        return isKeyValid;
    }

    public boolean removeFilterMessageIfKeyIsValid(String privKeyString) {
        if (isKeyValid(privKeyString)) {
            Filter filter = user.getDevelopersFilter();
            if (filter == null) {
                log.warn("Developers filter is null");
            } else if (p2PService.removeData(filter, true)) {
                log.trace("Remove filter from network was successful. FilterMessage = " + filter);
                user.setDevelopersFilter(null);
            } else {
                log.warn("Filter remove failed");
            }
            return true;
        } else {
            return false;
        }
    }

    private boolean isKeyValid(String privKeyString) {
        try {
            filterSigningKey = ECKey.fromPrivate(new BigInteger(1, HEX.decode(privKeyString)));
            return pubKeyAsHex.equals(Utils.HEX.encode(filterSigningKey.getPubKey()));
        } catch (Throwable t) {
            return false;
        }
    }

    private void signAndAddSignatureToFilter(Filter filter) {
        filter.setSigAndPubKey(filterSigningKey.signMessage(getHexFromData(filter)), keyRing.getSignatureKeyPair().getPublic());
    }

    private boolean verifySignature(Filter filter) {
        try {
            ECKey.fromPublicOnly(HEX.decode(pubKeyAsHex)).verifyMessage(getHexFromData(filter), filter.getSignatureAsBase64());
            return true;
        } catch (SignatureException e) {
            log.warn("verifySignature failed");
            return false;
        }
    }

    private String getHexFromData(Filter filter) {
        PB.Filter.Builder builder = PB.Filter.newBuilder().addAllBannedNodeAddress(filter.bannedNodeAddress)
                .addAllBannedOfferIds(filter.bannedOfferIds)
                .addAllBannedPaymentAccounts(filter.bannedPaymentAccounts.stream()
                        .map(PaymentAccountFilter::toProtoBuf)
                        .collect(Collectors.toList()));
        return Utils.HEX.encode(builder.build().toByteArray());
    }

    @Nullable
    public Filter getDevelopersFilter() {
        return user.getDevelopersFilter();
    }
}
