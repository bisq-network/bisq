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

package io.bisq.gui.common.view;

import io.bisq.common.app.Version;
import io.bisq.common.persistance.Persistable;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class ViewPath implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    @Getter
    @Delegate
    private ArrayList<Class<? extends View>> viewPathList = new ArrayList<>();

    private ViewPath() {
    }

    public ViewPath(Collection<? extends Class<? extends View>> c) {
        viewPathList = new ArrayList<>(c);
    }

    public static ViewPath to(Class<? extends View>... elements) {
        ViewPath path = new ViewPath();
        List<Class<? extends View>> list = Arrays.asList(elements);
        path.addAll(list);
        return path;
    }

    public static ViewPath from(ViewPath original) {
        ViewPath path = new ViewPath();
        path.addAll(original.getViewPathList());
        return path;
    }

    public Class<? extends View> tip() {
        if (viewPathList.size() == 0)
            return null;

        return viewPathList.get(viewPathList.size() - 1);
    }
}
