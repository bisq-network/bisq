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

    private static final String CURRENT_PATH_KEY = "currentPath";

    // TODO: MAIN->BUY is the default view for now; should be MAIN->HOME later
    private static final FxmlView[] DEFAULT_PATH = new FxmlView[]{ FxmlView.MAIN, FxmlView.BUY };

    // New listeners can be added during iteration so we use CopyOnWriteArrayList to
    // prevent invalid array modification
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private final Persistence persistence;

    private FxmlView[] currentPath;

    // Used for returning to the last important view. After setup is done we want to
    // return to the last opened view (e.g. sell/buy)
    private FxmlView[] returnPath;


    @Inject
    public Navigation(Persistence persistence) {
        this.persistence = persistence;
    }

    public void navigateTo(FxmlView... newPath) {
        if (newPath == null)
            return;

        List<FxmlView> temp = new ArrayList<>();
        for (int i = 0; i < newPath.length; i++) {
            FxmlView element = newPath[i];
            temp.add(element);
            if (currentPath == null ||
                    (currentPath != null &&
                            currentPath.length > i &&
                            element != currentPath[i] &&
                            i != newPath.length - 1)) {
                List<FxmlView> temp2 = new ArrayList<>(temp);
                for (int n = i + 1; n < newPath.length; n++) {
                    FxmlView[] newTemp = new FxmlView[i + 1];
                    currentPath = temp2.toArray(newTemp);
                    navigateTo(currentPath);
                    element = newPath[n];
                    temp2.add(element);
                }
            }
        }

        currentPath = newPath;
        persistence.write(this, CURRENT_PATH_KEY, currentPath);
        listeners.stream().forEach((e) -> e.onNavigationRequested(currentPath));
    }

    public void navigateToLastOpenView() {
        FxmlView[] lastPath = (FxmlView[]) persistence.read(this, CURRENT_PATH_KEY);

        if (lastPath == null || lastPath.length == 0)
            lastPath = DEFAULT_PATH;

        navigateTo(lastPath);
    }

    public static interface Listener {
        void onNavigationRequested(FxmlView... path);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public FxmlView[] getReturnPath() {
        return returnPath;
    }

    public FxmlView[] getCurrentPath() {
        return currentPath;
    }

    public void setReturnPath(FxmlView[] returnPath) {
        this.returnPath = returnPath;
    }

}
