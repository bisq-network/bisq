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

import com.google.protobuf.Message;
import io.bisq.common.app.Version;
import io.bisq.common.persistance.Persistable;
import io.bisq.common.storage.Storage;
import io.bisq.wire.proto.Messages;
import lombok.Getter;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.DeterministicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The List supporting our persistence solution.
 */
public final class AddressEntryList implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;
    private static final Logger log = LoggerFactory.getLogger(AddressEntryList.class);

    final transient private Storage<AddressEntryList> storage;
    transient private Wallet wallet;
    @Getter
    private List<AddressEntry> addressEntryList = new ArrayList<>();

    //@Inject
    public AddressEntryList(Storage<AddressEntryList> storage) {
        this.storage = storage;
    }

    public void onWalletReady(Wallet wallet) {
        this.wallet = wallet;

        AddressEntryList persisted = storage.initAndGetPersisted(this);
        if (persisted != null) {
            for (AddressEntry addressEntry : persisted.getAddressEntryList()) {
                DeterministicKey keyFromPubHash = (DeterministicKey) wallet.findKeyFromPubHash(addressEntry.getPubKeyHash());
                if (keyFromPubHash != null) {
                    addressEntry.setDeterministicKey(keyFromPubHash);
                    add(addressEntry);
                } else {
                    log.warn("Key from addressEntry not found in that wallet " + addressEntry.toString());
                }
            }
        } else {
            add(new AddressEntry(wallet.freshReceiveKey(), wallet.getParams(), AddressEntry.Context.ARBITRATOR));
            storage.queueUpForSave();
        }
    }

    private boolean add(AddressEntry addressEntry) {
        return addressEntryList.add(addressEntry);
    }

    private boolean remove(AddressEntry addressEntry) {
        return addressEntryList.remove(addressEntry);
    }

    public AddressEntry addAddressEntry(AddressEntry addressEntry) {
        boolean changed = add(addressEntry);
        if (changed)
            storage.queueUpForSave();
        return addressEntry;
    }


    public void swapTradeToSavings(String offerId) {
        Optional<AddressEntry> addressEntryOptional = addressEntryList.stream().filter(addressEntry -> offerId.equals(addressEntry.getOfferId())).findAny();
        if (addressEntryOptional.isPresent()) {
            AddressEntry addressEntry = addressEntryOptional.get();
            boolean changed1 = add(new AddressEntry(addressEntry.getKeyPair(), wallet.getParams(), AddressEntry.Context.AVAILABLE));
            boolean changed2 = remove(addressEntry);
            if (changed1 || changed2)
                storage.queueUpForSave();
        }
    }


    public void swapToAvailable(AddressEntry addressEntry) {
        remove(addressEntry);
        boolean changed1 = add(new AddressEntry(addressEntry.getKeyPair(), wallet.getParams(), AddressEntry.Context.AVAILABLE));
        boolean changed2 = remove(addressEntry);
        if (changed1 || changed2)
            storage.queueUpForSave();
    }

    public void queueUpForSave() {
        storage.queueUpForSave(50);
    }

    @Override
    public Message toProtobuf() {
        return Messages.AddressEntryList.newBuilder()
                .addAllAddressEntry(getAddressEntryList().stream()
                        .map(addressEntry -> ((Messages.AddressEntry) addressEntry.toProtobuf()))
                        .collect(Collectors.toList()))
                .build();
    }
}
