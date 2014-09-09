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

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NavigationController {
    private static final Logger log = LoggerFactory.getLogger(NavigationController.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface NavigationListener {
        void onNavigationRequested(NavigationItem... navigationItems);
    }


    private List<NavigationListener> listeners = new ArrayList<>();
    private NavigationItem[] previousMainNavigationItems;
    private NavigationItem[] currentNavigationItems;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public NavigationController() {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void navigationTo(NavigationItem... navigationItems) {
        previousMainNavigationItems = currentNavigationItems;
        currentNavigationItems = navigationItems;

        listeners.stream().forEach((e) -> e.onNavigationRequested(navigationItems));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListener(NavigationListener listener) {
        listeners.add(listener);
    }

    public void removeListener(NavigationListener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public NavigationItem[] getPreviousMainNavigationItems() {
        return previousMainNavigationItems;
    }

    public NavigationItem[] getCurrentNavigationItems() {
        return currentNavigationItems;
    }

}
