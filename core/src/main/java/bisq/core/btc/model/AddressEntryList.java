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

package bisq.core.btc.model;

import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.wallet.Wallet;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * The List supporting our persistence solution.
 */
@ToString
@Slf4j
public final class AddressEntryList implements PersistableEnvelope, PersistedDataHost {
    transient private Storage<AddressEntryList> storage;
    transient private Wallet wallet;
    @Getter
    private List<AddressEntry> list;

    @Inject
    public AddressEntryList(Storage<AddressEntryList> storage) {
        this.storage = storage;
    }

    @Override
    public void readPersisted() {
        AddressEntryList persisted = storage.initAndGetPersisted(this, 50);
        if (persisted != null)
            list = new ArrayList<>(persisted.getList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private AddressEntryList(List<AddressEntry> list) {
        this.list = list;
    }

    public static AddressEntryList fromProto(PB.AddressEntryList proto) {
        return new AddressEntryList(new ArrayList<>(proto.getAddressEntryList().stream().map(AddressEntry::fromProto).collect(Collectors.toList())));
    }

    @Override
    public Message toProtoMessage() {
        // We clone here as we got ConcurrentModificationExceptions
        ArrayList<AddressEntry> clone = new ArrayList<>(this.list);
        List<PB.AddressEntry> addressEntries = clone.stream()
                .map(AddressEntry::toProtoMessage)
                .collect(Collectors.toList());
        return PB.PersistableEnvelope.newBuilder()
                .setAddressEntryList(PB.AddressEntryList.newBuilder()
                        .addAllAddressEntry(addressEntries))
                .build();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onWalletReady(Wallet wallet) {
        this.wallet = wallet;

        if (list != null) {
            list.forEach(addressEntry -> {
                DeterministicKey keyFromPubHash = (DeterministicKey) wallet.findKeyFromPubHash(addressEntry.getPubKeyHash());
                if (keyFromPubHash != null) {
                    addressEntry.setDeterministicKey(keyFromPubHash);
                } else {
                    log.error("Key from addressEntry not found in that wallet " + addressEntry.toString());
                }
            });
        } else {
            list = new ArrayList<>();
            add(new AddressEntry(wallet.freshReceiveKey(), AddressEntry.Context.ARBITRATOR));

            // In case we restore from seed words and have balance we need to add the relevant addresses to our list:
            if (wallet.getBalance().isPositive()) {
                wallet.getIssuedReceiveAddresses().forEach(address -> {
                    log.info("Create AddressEntry for address={}", address);
                    add(new AddressEntry((DeterministicKey) wallet.findKeyFromPubHash(address.getHash160()), AddressEntry.Context.AVAILABLE));
                });
            }

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
                .findAny().ifPresent(this::swapToAvailable);
    }

    public void swapToAvailable(AddressEntry addressEntry) {
        boolean changed1 = remove(addressEntry);
        boolean changed2 = add(new AddressEntry(addressEntry.getKeyPair(), AddressEntry.Context.AVAILABLE));
        if (changed1 || changed2)
            persist();
    }

    public AddressEntry swapAvailableToAddressEntryWithOfferId(AddressEntry addressEntry, AddressEntry.Context context, String offerId) {
        boolean changed1 = remove(addressEntry);
        final AddressEntry newAddressEntry = new AddressEntry(addressEntry.getKeyPair(), context, offerId);
        boolean changed2 = add(newAddressEntry);
        if (changed1 || changed2)
            persist();

        return newAddressEntry;
    }

    public void persist() {
        storage.queueUpForSave(50);
    }

    public Stream<AddressEntry> stream() {
        return list.stream();
    }
}
