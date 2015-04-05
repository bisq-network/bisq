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

import io.bitsquare.common.viewfx.view.View;
import io.bitsquare.common.viewfx.view.ViewPath;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.offer.BuyOfferView;
import io.bitsquare.storage.Storage;

import com.google.inject.Inject;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Navigation implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;
    transient private static final Logger log = LoggerFactory.getLogger(Navigation.class);

    transient private static final ViewPath DEFAULT_VIEW_PATH = ViewPath.to(MainView.class, BuyOfferView.class);


    public interface Listener {
        void onNavigationRequested(ViewPath path);
    }

    // New listeners can be added during iteration so we use CopyOnWriteArrayList to
    // prevent invalid array modification
    transient private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    transient private final Storage<Navigation> storage;
    transient private ViewPath currentPath;
    // Used for returning to the last important view. After setup is done we want to
    // return to the last opened view (e.g. sell/buy)
    transient private ViewPath returnPath;

    // Persisted fields
    private ViewPath previousPath;


    @Inject
    public Navigation(Storage<Navigation> storage) {
        this.storage = storage;

        Navigation persisted = storage.initAndGetPersisted(this);
        if (persisted != null) {
            previousPath = persisted.getPreviousPath();
        }
        else
            previousPath = DEFAULT_VIEW_PATH;

        // need to be null initially and not DEFAULT_VIEW_PATH to navigate through all items
        currentPath = null;
    }

    @SuppressWarnings("unchecked")
    public void navigateTo(Class<? extends View>... viewClasses) {
        navigateTo(ViewPath.to(viewClasses));
    }

    public void navigateTo(ViewPath newPath) {
        if (newPath == null)
            return;

        ArrayList<Class<? extends View>> temp = new ArrayList<>();
        for (int i = 0; i < newPath.size(); i++) {
            Class<? extends View> viewClass = newPath.get(i);
            temp.add(viewClass);
            if (currentPath == null ||
                    (currentPath != null &&
                            currentPath.size() > i &&
                            viewClass != currentPath.get(i) &&
                            i != newPath.size() - 1)) {
                ArrayList<Class<? extends View>> temp2 = new ArrayList<>(temp);
                for (int n = i + 1; n < newPath.size(); n++) {
                    Class<? extends View>[] newTemp = new Class[i + 1];
                    currentPath = ViewPath.to(temp2.toArray(newTemp));
                    navigateTo(currentPath);
                    viewClass = newPath.get(n);
                    temp2.add(viewClass);
                }
            }
        }

        currentPath = newPath;
        previousPath = currentPath;
        storage.queueUpForSave();
        listeners.stream().forEach((e) -> e.onNavigationRequested(currentPath));
    }

    public void navigateToPreviousVisitedView() {
        if (previousPath == null || previousPath.size() == 0)
            previousPath = DEFAULT_VIEW_PATH;

        navigateTo(previousPath);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public ViewPath getReturnPath() {
        return returnPath;
    }

    public ViewPath getCurrentPath() {
        return currentPath;
    }

    public void setReturnPath(ViewPath returnPath) {
        this.returnPath = returnPath;
    }

    private ViewPath getPreviousPath() {
        return previousPath;
    }
}
