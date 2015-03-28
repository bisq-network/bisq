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

package io.bitsquare.trade;

import io.bitsquare.storage.Storage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import java.util.ArrayList;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeList<T> extends ArrayList<T> implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(TradeList.class);

    transient final private Storage<TradeList> storage;
    // Use getObservableList() also class locally, to be sure that object exists in case we use the object as deserialized form
    transient private ObservableList<T> observableList;

    // Superclass is ArrayList, which will be persisted

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradeList(Storage<TradeList> storage, String fileName) {
        log.trace("Created by constructor");
        this.storage = storage;

        TradeList persisted = storage.initAndGetPersisted(this, fileName);
        if (persisted != null) {
            this.addAll(persisted);
        }
        observableList = FXCollections.observableArrayList(this);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        log.trace("Created from serialized form.");
    }

    @Override
    public boolean add(T trade) {
        boolean result = super.add(trade);
        getObservableList().add(trade);
        storage.queueUpForSave();
        return result;
    }

    @Override
    public boolean remove(Object trade) {
        boolean result = super.remove(trade);
        getObservableList().remove(trade);
        storage.queueUpForSave();
        return result;
    }

    public ObservableList<T> getObservableList() {
        if (observableList == null)
            observableList = FXCollections.observableArrayList(this);
        return observableList;
    }

}
