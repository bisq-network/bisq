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

package io.bisq.core.btc;

import com.google.inject.Inject;
import com.google.protobuf.Message;
import io.bisq.common.app.Version;
import io.bisq.common.persistence.Persistable;
import io.bisq.common.storage.Storage;
import io.bisq.generated.protobuffer.PB;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.wallet.Wallet;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The List supporting our persistence solution.
 */
@ToString
@Slf4j
public final class AddressEntryList implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    final transient private Storage<AddressEntryList> storage;
    transient private Wallet wallet;
    @Getter
    private List<AddressEntry> list = new ArrayList<>();
    @Setter
    private boolean doPersist;

    @Inject
    public AddressEntryList(Storage<AddressEntryList> storage) {
        this.storage = storage;
    }

    public void onWalletReady(Wallet wallet) {
        this.wallet = wallet;

        AddressEntryList persisted = storage.initAndGetPersisted(this);
        if (persisted != null) {
            for (AddressEntry addressEntry : persisted.getList()) {
                DeterministicKey keyFromPubHash = (DeterministicKey) wallet.findKeyFromPubHash(addressEntry.getPubKeyHash());
                if (keyFromPubHash != null) {
                    addressEntry.setDeterministicKey(keyFromPubHash);
                } else {
                    log.warn("Key from addressEntry not found in that wallet " + addressEntry.toString());
                }
            }
        } else {
            doPersist = true;
            add(new AddressEntry(wallet.freshReceiveKey(), AddressEntry.Context.ARBITRATOR));
            persist();
        }
    }

    private boolean add(AddressEntry addressEntry) {
        return list.add(addressEntry);
    }

    private boolean remove(AddressEntry addressEntry) {
        return list.remove(addressEntry);
    }

    public AddressEntry addAddressEntry(AddressEntry addressEntry) {
        boolean changed = add(addressEntry);
        if (changed)
            persist();
        return addressEntry;
    }

    public void swapTradeToSavings(String offerId) {
        list.stream().filter(addressEntry -> offerId.equals(addressEntry.getOfferId()))
                .findAny().ifPresent(addressEntry -> {
            boolean changed1 = add(new AddressEntry(addressEntry.getKeyPair(), AddressEntry.Context.AVAILABLE));
            boolean changed2 = remove(addressEntry);
            if (changed1 || changed2)
                persist();
        });
    }

    public void swapToAvailable(AddressEntry addressEntry) {
        remove(addressEntry);
        boolean changed1 = add(new AddressEntry(addressEntry.getKeyPair(), AddressEntry.Context.AVAILABLE));
        boolean changed2 = remove(addressEntry);
        if (changed1 || changed2)
            persist();
    }

    public Stream<AddressEntry> stream() {
        return list.stream();
    }

    public void persist() {
        if (doPersist)
            storage.queueUpForSave(50);
    }

    @Override
    public Message toProto() {
        final PB.Persistable build = PB.Persistable.newBuilder().setAddressEntryList(PB.AddressEntryList.newBuilder()
                .addAllAddressEntry(stream()
                        .map(addressEntry -> ((PB.AddressEntry) addressEntry.toProto()))
                        .collect(Collectors.toList())))
                .build();
        return build;
    }
}
