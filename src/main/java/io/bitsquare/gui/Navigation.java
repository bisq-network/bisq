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

package io.bitsquare.gui;

import io.bitsquare.persistence.Persistence;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Navigation {
    // New listeners can be added during iteration so we use CopyOnWriteArrayList to prevent invalid array
    // modification
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Persistence persistence;
    private FxmlView[] currentItems;

    // Used for returning to the last important view
    // After setup is done we want to return to the last opened view (e.g. sell/buy)
    private FxmlView[] itemsForReturning;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Navigation(Persistence persistence) {
        this.persistence = persistence;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void navigationTo(FxmlView... items) {
        List<FxmlView> temp = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.length; i++) {
                FxmlView item = items[i];
                temp.add(item);
                if (currentItems == null ||
                        (currentItems != null &&
                                currentItems.length > i &&
                                item != currentItems[i] &&
                                i != items.length - 1)) {
                    List<FxmlView> temp2 = new ArrayList<>(temp);
                    for (int n = i + 1; n < items.length; n++) {
                        FxmlView[] newTemp = new FxmlView[i + 1];
                        currentItems = temp2.toArray(newTemp);
                        navigationTo(currentItems);
                        item = items[n];
                        temp2.add(item);
                    }
                }
            }

            currentItems = items;

            persistence.write(this, "navigationItems", items);
            listeners.stream().forEach((e) -> e.onNavigationRequested(items));
        }
    }

    public void navigateToLastStoredItem() {
        FxmlView[] items = (FxmlView[]) persistence.read(this, "navigationItems");
        // TODO we set BUY as default yet, should be HOME later
        if (items == null || items.length == 0)
            items = new FxmlView[]{FxmlView.MAIN, FxmlView.BUY};

        navigationTo(items);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public FxmlView[] getItemsForReturning() {
        return itemsForReturning;
    }

    public FxmlView[] getCurrentItems() {
        return currentItems;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setItemsForReturning(FxmlView[] itemsForReturning) {
        this.itemsForReturning = itemsForReturning;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static interface Listener {
        void onNavigationRequested(FxmlView... items);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

}
