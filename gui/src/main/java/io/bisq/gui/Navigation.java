/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.protobuf.Message;
import io.bisq.common.app.Version;
import io.bisq.common.persistence.Persistable;
import io.bisq.common.storage.Storage;
import io.bisq.generated.protobuffer.PB;
import io.bisq.gui.common.view.View;
import io.bisq.gui.common.view.ViewPath;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.market.MarketView;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

public final class Navigation implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;
    private static final Logger log = LoggerFactory.getLogger(Navigation.class);

    private static final ViewPath DEFAULT_VIEW_PATH = ViewPath.to(MainView.class, MarketView.class);


    public interface Listener {
        void onNavigationRequested(ViewPath path);
    }

    // New listeners can be added during iteration so we use CopyOnWriteArrayList to
    // prevent invalid array modification
    transient private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();
    transient private final Storage<Navigation> storage;
    transient private ViewPath currentPath;
    // Used for returning to the last important view. After setup is done we want to
    // return to the last opened view (e.g. sell/buy)
    transient private ViewPath returnPath;

    // Persisted fields
    @Getter
    @Setter
    private ViewPath previousPath;


    @Inject
    public Navigation(Storage<Navigation> storage) {
        this.storage = storage;
        storage.setNumMaxBackupFiles(3);

        Navigation persisted = storage.initAndGetPersisted(this);
        if (persisted != null) {
            previousPath = persisted.getPreviousPath();
        } else
            previousPath = DEFAULT_VIEW_PATH;

        // need to be null initially and not DEFAULT_VIEW_PATH to navigate through all items
        currentPath = null;
    }

    /** used for deserialisation/fromProto */
    public Navigation(List<Class<? extends View>> classes) {
        previousPath = new ViewPath(Lists.newArrayList());
        previousPath.addAll(classes);

        // need to be null initially and not DEFAULT_VIEW_PATH to navigate through all items
        currentPath = null;
        storage = null;
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
        storage.queueUpForSave(1000);
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


    @Override
    public Message toProto() {
        return PB.Navigation.newBuilder().setPreviousPath(PB.ViewPath.newBuilder()
                .addAllViewPath(previousPath.stream()
                        .map(aClass -> aClass.getName()).collect(Collectors.toList()))).build();
    }

    public static Navigation fromProto(PB.Navigation proto) {
        List<Class<? extends View>> classStream = proto.getPreviousPath().getViewPathList().stream().map(s -> {
            try {
                return ((Class<? extends View>) Class.forName(s));
            } catch (ClassNotFoundException e) {
                log.warn("Could not find the Viewpath class {}; exception: {}", s, e);
            }
            return null;
        }).collect(Collectors.toList());
        return new Navigation(classStream);
    }
}
