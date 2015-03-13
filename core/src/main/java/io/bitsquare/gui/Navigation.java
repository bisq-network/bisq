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

import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.trade.BuyView;
import io.bitsquare.persistence.Persistence;

import com.google.inject.Inject;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.bitsquare.viewfx.view.View;
import io.bitsquare.viewfx.view.ViewPath;

public class Navigation {

    private static final String CURRENT_PATH_KEY = "currentPath";

    private static final ViewPath DEFAULT_VIEW_PATH = ViewPath.to(MainView.class, BuyView.class);

    // New listeners can be added during iteration so we use CopyOnWriteArrayList to
    // prevent invalid array modification
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private final Persistence persistence;

    private ViewPath currentPath;

    // Used for returning to the last important view. After setup is done we want to
    // return to the last opened view (e.g. sell/buy)
    private ViewPath returnPath;


    @Inject
    public Navigation(Persistence persistence) {
        this.persistence = persistence;
    }

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
        persistence.write(this, CURRENT_PATH_KEY, (List<? extends Serializable>)currentPath);
        listeners.stream().forEach((e) -> e.onNavigationRequested(currentPath));
    }

    public void navigateToLastOpenView() {
        ViewPath lastPath = (ViewPath) persistence.read(this, CURRENT_PATH_KEY);

        if (lastPath == null || lastPath.size() == 0)
            lastPath = DEFAULT_VIEW_PATH;

        navigateTo(lastPath);
    }

    public static interface Listener {
        void onNavigationRequested(ViewPath path);
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
}
