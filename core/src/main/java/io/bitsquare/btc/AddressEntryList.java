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

import io.bitsquare.storage.Storage;

import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.DeterministicKey;

import com.google.inject.Inject;

import java.io.Serializable;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddressEntryList extends ArrayList<AddressEntry> implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;
    transient private static final Logger log = LoggerFactory.getLogger(AddressEntryList.class);

    transient private Storage<AddressEntryList> storage;
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
                addressEntry.setDeterministicKey((DeterministicKey) wallet.findKeyFromPubHash(addressEntry.getPubKeyHash()));
                this.add(addressEntry);
            }
        }
        else {
            // First time create registrationAddressEntry
            createRegistrationAddressEntry();
        }
    }

    public AddressEntry getNewAddressEntry(AddressEntry.Context context, String offerId) {
        log.trace("getNewAddressEntry called with offerId " + offerId);
        DeterministicKey key = wallet.freshReceiveKey();
        AddressEntry addressEntry = new AddressEntry(key, wallet.getParams(), context, offerId);
        add(addressEntry);
        storage.queueUpForSave();
        return addressEntry;
    }

    private void createRegistrationAddressEntry() {
        DeterministicKey registrationKey = wallet.currentReceiveKey();
        AddressEntry registrationAddressEntry = new AddressEntry(registrationKey, wallet.getParams(), AddressEntry.Context.REGISTRATION_FEE);
        add(registrationAddressEntry);
        storage.queueUpForSave();
    }

    public AddressEntry getRegistrationAddressEntry() {
        if (isEmpty())
            createRegistrationAddressEntry();

        return get(0);
    }
}
