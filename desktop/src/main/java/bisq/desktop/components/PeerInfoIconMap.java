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

package bisq.desktop.components;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import java.util.HashMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PeerInfoIconMap extends HashMap<String, PeerInfoIcon> implements ChangeListener<String> {

    @Override
    public PeerInfoIcon put(String key, PeerInfoIcon icon) {
        icon.tagProperty().addListener(this);
        return super.put(key, icon);
    }

    @Override
    public void changed(ObservableValue<? extends String> o, String oldVal, String newVal) {
        log.info("Updating avatar tags, the avatar map size is {}", size());
        forEach((key, icon) -> {
            // We update all avatars, as some could be sharing the same tag.
            // We also temporarily remove listeners to prevent firing of
            // events while each icon's tagProperty is being reset.
            icon.tagProperty().removeListener(this);
            icon.refreshTag();
            icon.tagProperty().addListener(this);
        });
    }
}
