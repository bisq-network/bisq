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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NavigationManager {
    private static final Logger log = LoggerFactory.getLogger(NavigationManager.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onNavigationRequested(NavigationItem... navigationItems);
    }

    // New listeners can be added during iteration so we use CopyOnWriteArrayList to prevent invalid array 
    // modification 
    private List<Listener> listeners = new CopyOnWriteArrayList<>();
    private Persistence persistence;
    private NavigationItem[] currentNavigationItems;

    // Used for returning to the last important view
    // After setup is done we want to return to the last opened view (e.g. sell/buy)
    private NavigationItem[] navigationItemsForReturning;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public NavigationManager(Persistence persistence) {
        this.persistence = persistence;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void navigationTo(NavigationItem... navigationItems) {
        log.trace("navigationTo " + Arrays.asList(navigationItems).toString());
        List<NavigationItem> temp = new ArrayList<>();
        for (int i = 0; i < navigationItems.length; i++) {
            NavigationItem item = navigationItems[i];
            temp.add(item);
            if (currentNavigationItems == null ||
                    (currentNavigationItems != null &&
                            currentNavigationItems.length > i &&
                            item != currentNavigationItems[i] &&
                            i != navigationItems.length - 1)) {
                List<NavigationItem> temp2 = new ArrayList<>(temp);
                for (int n = i + 1; n < navigationItems.length; n++) {
                    NavigationItem[] newTemp = new NavigationItem[i + 1];
                    currentNavigationItems = temp2.toArray(newTemp);
                    navigationTo(currentNavigationItems);
                    item = navigationItems[n];
                    temp2.add(item);
                }
            }
        }
        currentNavigationItems = navigationItems;

        persistence.write(this, "navigationItems", navigationItems);
        log.trace("navigationTo notify listeners " + Arrays.asList(navigationItems).toString() + " / " + listeners
                .size());
        listeners.stream().forEach((e) -> e.onNavigationRequested(navigationItems));
    }

    public void navigateToLastStoredItem() {
        NavigationItem[] navigationItems = (NavigationItem[]) persistence.read(this, "navigationItems");
        if (navigationItems == null || navigationItems.length == 0)
            navigationItems = new NavigationItem[]{NavigationItem.MAIN, NavigationItem.HOME};

        navigationTo(navigationItems);
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

    public NavigationItem[] getNavigationItemsForReturning() {
        return navigationItemsForReturning;
    }

    public NavigationItem[] getCurrentNavigationItems() {
        return currentNavigationItems;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setNavigationItemsForReturning(NavigationItem[] navigationItemsForReturning) {
        this.navigationItemsForReturning = navigationItemsForReturning;
    }

}
