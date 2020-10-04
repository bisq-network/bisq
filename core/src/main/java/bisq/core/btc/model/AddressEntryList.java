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

import bisq.common.config.Config;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistedDataHost;

import com.google.protobuf.Message;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.Wallet;

import com.google.inject.Inject;

import com.google.common.collect.ImmutableList;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * The AddressEntries was previously stored as list, now as hashSet. We still keep the old name to reflect the
 * associated protobuf message.
 */
@Slf4j
public final class AddressEntryList implements PersistableEnvelope, PersistedDataHost {
    transient private PersistenceManager<AddressEntryList> persistenceManager;
    transient private Wallet wallet;
    private final Set<AddressEntry> entrySet = new CopyOnWriteArraySet<>();

    @Inject
    public AddressEntryList(PersistenceManager<AddressEntryList> persistenceManager) {
        this.persistenceManager = persistenceManager;

        this.persistenceManager.initialize(this, PersistenceManager.Source.PRIVATE);
    }

    @Override
    public void readPersisted() {
        AddressEntryList persisted = persistenceManager.getPersisted();
        if (persisted != null) {
            entrySet.clear();
            entrySet.addAll(persisted.entrySet);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private AddressEntryList(Set<AddressEntry> entrySet) {
        this.entrySet.addAll(entrySet);
    }

    public static AddressEntryList fromProto(protobuf.AddressEntryList proto) {
        Set<AddressEntry> entrySet = proto.getAddressEntryList().stream()
                .map(AddressEntry::fromProto)
                .collect(Collectors.toSet());
        return new AddressEntryList(entrySet);
    }

    @Override
    public Message toProtoMessage() {
        Set<protobuf.AddressEntry> addressEntries = entrySet.stream()
                .map(AddressEntry::toProtoMessage)
                .collect(Collectors.toSet());
        return protobuf.PersistableEnvelope.newBuilder()
                .setAddressEntryList(protobuf.AddressEntryList.newBuilder()
                        .addAllAddressEntry(addressEntries))
                .build();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onWalletReady(Wallet wallet) {
        this.wallet = wallet;

        if (!entrySet.isEmpty()) {
            Set<AddressEntry> toBeRemoved = new HashSet<>();
            entrySet.forEach(addressEntry -> {
                DeterministicKey keyFromPubHash = (DeterministicKey) wallet.findKeyFromPubKeyHash(
                        addressEntry.getPubKeyHash(),
                        Script.ScriptType.P2PKH);
                if (keyFromPubHash != null) {
                    Address addressFromKey = LegacyAddress.fromKey(Config.baseCurrencyNetworkParameters(), keyFromPubHash);
                    // We want to ensure key and address matches in case we have address in entry available already
                    if (addressEntry.getAddress() == null || addressFromKey.equals(addressEntry.getAddress())) {
                        addressEntry.setDeterministicKey(keyFromPubHash);
                    } else {
                        log.error("We found an address entry without key but cannot apply the key as the address " +
                                        "is not matching. " +
                                        "We remove that entry as it seems it is not compatible with our wallet. " +
                                        "addressFromKey={}, addressEntry.getAddress()={}",
                                addressFromKey, addressEntry.getAddress());
                        toBeRemoved.add(addressEntry);
                    }
                } else {
                    log.error("Key from addressEntry {} not found in that wallet. We remove that entry. " +
                            "This is expected at restore from seeds.", addressEntry.toString());
                    toBeRemoved.add(addressEntry);
                }
            });

            toBeRemoved.forEach(entrySet::remove);
        } else {
            // As long the old arbitration domain is not removed from the code base we still support it here.
            entrySet.add(new AddressEntry(wallet.freshReceiveKey(), AddressEntry.Context.ARBITRATOR));
        }

        // In case we restore from seed words and have balance we need to add the relevant addresses to our list.
        // IssuedReceiveAddresses does not contain all addresses where we expect balance so we need to listen to
        // incoming txs at blockchain sync to add the rest.
        if (wallet.getBalance().isPositive()) {
            wallet.getIssuedReceiveAddresses().stream()
                    .filter(this::isAddressNotInEntries)
                    .forEach(address -> {
                        log.info("Create AddressEntry for IssuedReceiveAddress. address={}", address.toString());
                        DeterministicKey key = (DeterministicKey) wallet.findKeyFromAddress(address);
                        if (key != null) {
                            // Address will be derived from key in getAddress method
                            entrySet.add(new AddressEntry(key, AddressEntry.Context.AVAILABLE));
                        }
                    });
        }

        // We add those listeners to get notified about potential new transactions and
        // add an address entry list in case it does not exist yet. This is mainly needed for restore from seed words
        // but can help as well in case the addressEntry list would miss an address where the wallet was received
        // funds (e.g. if the user sends funds to an address which has not been provided in the main UI - like from the
        // wallet details window).
        wallet.addCoinsReceivedEventListener((wallet1, tx, prevBalance, newBalance) -> {
            maybeAddNewAddressEntry(tx);
        });
        wallet.addCoinsSentEventListener((wallet1, tx, prevBalance, newBalance) -> {
            maybeAddNewAddressEntry(tx);
        });

        requestPersistence();
    }

    public ImmutableList<AddressEntry> getAddressEntriesAsListImmutable() {
        return ImmutableList.copyOf(entrySet);
    }

    public void addAddressEntry(AddressEntry addressEntry) {
        boolean entryWithSameOfferIdAndContextAlreadyExist = entrySet.stream().anyMatch(e -> {
            if (addressEntry.getOfferId() != null) {
                return addressEntry.getOfferId().equals(e.getOfferId()) && addressEntry.getContext() == e.getContext();
            }
            return false;
        });
        if (entryWithSameOfferIdAndContextAlreadyExist) {
            log.error("We have an address entry with the same offer ID and context. We do not add the new one. " +
                    "addressEntry={}, entrySet={}", addressEntry, entrySet);
            return;
        }

        boolean setChangedByAdd = entrySet.add(addressEntry);
        if (setChangedByAdd)
            requestPersistence();
    }

    public void swapToAvailable(AddressEntry addressEntry) {
        boolean setChangedByRemove = entrySet.remove(addressEntry);
        boolean setChangedByAdd = entrySet.add(new AddressEntry(addressEntry.getKeyPair(),
                AddressEntry.Context.AVAILABLE));
        if (setChangedByRemove || setChangedByAdd) {
            requestPersistence();
        }
    }

    public AddressEntry swapAvailableToAddressEntryWithOfferId(AddressEntry addressEntry,
                                                               AddressEntry.Context context,
                                                               String offerId) {
        boolean setChangedByRemove = entrySet.remove(addressEntry);
        final AddressEntry newAddressEntry = new AddressEntry(addressEntry.getKeyPair(), context, offerId);
        boolean setChangedByAdd = entrySet.add(newAddressEntry);
        if (setChangedByRemove || setChangedByAdd)
            requestPersistence();

        return newAddressEntry;
    }

    public void requestPersistence() {
        persistenceManager.requestPersistence();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void maybeAddNewAddressEntry(Transaction tx) {
        tx.getOutputs().stream()
                .filter(output -> output.isMine(wallet))
                .map(output -> output.getScriptPubKey().getToAddress(wallet.getNetworkParameters()))
                .filter(Objects::nonNull)
                .filter(this::isAddressNotInEntries)
                .map(address -> (DeterministicKey) wallet.findKeyFromPubKeyHash(address.getHash(),
                        Script.ScriptType.P2PKH))
                .filter(Objects::nonNull)
                .map(deterministicKey -> new AddressEntry(deterministicKey, AddressEntry.Context.AVAILABLE))
                .forEach(this::addAddressEntry);
    }

    private boolean isAddressNotInEntries(Address address) {
        return entrySet.stream().noneMatch(e -> address.equals(e.getAddress()));
    }

    @Override
    public String toString() {
        return "AddressEntryList{" +
                ",\n     entrySet=" + entrySet +
                "\n}";
    }
}
