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

import io.bitsquare.app.Version;
import io.bitsquare.common.persistance.Persistable;
import io.bitsquare.storage.Storage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;

public class TradableList<T extends Tradable> extends ArrayList<T> implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(TradableList.class);

    transient final private Storage<TradableList<T>> storage;
    // Use getObservableList() also class locally, to be sure that object exists in case we use the object as deserialized form
    transient private ObservableList<T> observableList;

    // Superclass is ArrayList, which will be persisted

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradableList(Storage<TradableList<T>> storage, String fileName) {
        this.storage = storage;

        TradableList persisted = storage.initAndGetPersisted(this, fileName);
        if (persisted != null) {
            this.addAll(persisted);
        }
        observableList = FXCollections.observableArrayList(this);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (Throwable t) {
            log.trace("Cannot be deserialized." + t.getMessage());
        }
    }

    @Override
    public boolean add(T tradable) {
        boolean result = super.add(tradable);
        getObservableList().add(tradable);
        storage.queueUpForSave();
        return result;
    }

    @Override
    public boolean remove(Object tradable) {
        boolean result = super.remove(tradable);
        getObservableList().remove(tradable);
        storage.queueUpForSave();
        return result;
    }

    public ObservableList<T> getObservableList() {
        if (observableList == null)
            observableList = FXCollections.observableArrayList(this);
        return observableList;
    }

}
