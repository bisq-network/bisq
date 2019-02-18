/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop;

import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewPath;
import bisq.desktop.main.MainView;
import bisq.desktop.main.market.MarketView;

import bisq.common.proto.persistable.NavigationPath;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public final class Navigation implements PersistedDataHost {
    private static final ViewPath DEFAULT_VIEW_PATH = ViewPath.to(MainView.class, MarketView.class);

    public interface Listener {
        void onNavigationRequested(ViewPath path);

        default void onNavigationRequested(ViewPath path, @Nullable Object data) {
        }
    }

    // New listeners can be added during iteration so we use CopyOnWriteArrayList to
    // prevent invalid array modification
    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();
    private final Storage<NavigationPath> storage;
    private ViewPath currentPath;
    // Used for returning to the last important view. After setup is done we want to
    // return to the last opened view (e.g. sell/buy)
    private ViewPath returnPath;
    // this string is updated just before saving to disk so it reflects the latest currentPath situation.
    private final NavigationPath navigationPath = new NavigationPath();

    // Persisted fields
    @Getter
    @Setter
    private ViewPath previousPath = DEFAULT_VIEW_PATH;


    @Inject
    public Navigation(Storage<NavigationPath> storage) {
        this.storage = storage;
        storage.setNumMaxBackupFiles(3);
    }

    @Override
    public void readPersisted() {
        NavigationPath persisted = storage.initAndGetPersisted(navigationPath, "NavigationPath", 300);
        if (persisted != null) {
            List<Class<? extends View>> viewClasses = persisted.getPath().stream()
                    .map(className -> {
                        try {
                            //noinspection unchecked
                            return ((Class<? extends View>) Class.forName(className));
                        } catch (ClassNotFoundException e) {
                            log.warn("Could not find the viewPath class {}; exception: {}", className, e);
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (!viewClasses.isEmpty())
                previousPath = new ViewPath(viewClasses);
        }
    }

    @SafeVarargs
    public final void navigateTo(Class<? extends View>... viewClasses) {
        navigateTo(ViewPath.to(viewClasses), null);
    }

    @SafeVarargs
    public final void navigateToWithData(Object data, Class<? extends View>... viewClasses) {
        navigateTo(ViewPath.to(viewClasses), data);
    }

    public void navigateTo(ViewPath newPath, @Nullable Object data) {
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
                    //noinspection unchecked,unchecked,unchecked
                    Class<? extends View>[] newTemp = new Class[i + 1];
                    currentPath = ViewPath.to(temp2.toArray(newTemp));
                    navigateTo(currentPath, data);
                    viewClass = newPath.get(n);
                    temp2.add(viewClass);
                }
            }
        }

        currentPath = newPath;
        previousPath = currentPath;
        queueUpForSave();
        listeners.forEach((e) -> e.onNavigationRequested(currentPath));
        listeners.forEach((e) -> e.onNavigationRequested(currentPath, data));
    }

    private void queueUpForSave() {
        if (currentPath.tip() != null) {
            navigationPath.setPath(currentPath.stream().map(Class::getName).collect(Collectors.toList()));
        }
        storage.queueUpForSave(navigationPath, 1000);
    }

    public void navigateToPreviousVisitedView() {
        if (previousPath == null || previousPath.size() == 0)
            previousPath = DEFAULT_VIEW_PATH;

        navigateTo(previousPath, null);
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
