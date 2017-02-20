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

package io.bitsquare.btc;

import com.google.inject.Inject;
import io.bitsquare.app.Version;
import io.bitsquare.common.persistance.Persistable;
import io.bitsquare.storage.Storage;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.DeterministicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Optional;

/**
 * The List supporting our persistence solution.
 */
public final class AddressEntryList extends ArrayList<AddressEntry> implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;
    private static final Logger log = LoggerFactory.getLogger(AddressEntryList.class);

    final transient private Storage<AddressEntryList> storage;
    transient private Wallet wallet;

    // Persisted fields are in ArrayList superclass

    @Inject
    public AddressEntryList(Storage<AddressEntryList> storage) {
        this.storage = storage;
    }

    public void onWalletReady(Wallet wallet) {
        this.wallet = wallet;

        AddressEntryList persisted = storage.initAndGetPersisted(this);
        if (persisted != null) {
            for (AddressEntry addressEntry : persisted) {
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

    public AddressEntry addAddressEntry(AddressEntry addressEntry) {
        boolean changed = add(addressEntry);
        if (changed)
            storage.queueUpForSave();
        return addressEntry;
    }


    public void swapTradeToSavings(String offerId) {
        Optional<AddressEntry> addressEntryOptional = this.stream().filter(addressEntry -> offerId.equals(addressEntry.getOfferId())).findAny();
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
}
