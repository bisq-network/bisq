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

package bisq.desktop.common.view;

import bisq.desktop.common.model.Activatable;

import javafx.scene.Node;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class ActivatableViewAndModel<R extends Node, M extends Activatable> extends ActivatableView<R, M> {

    public ActivatableViewAndModel(M model) {
        super(checkNotNull(model, "Model must not be null"));
    }

    @Override
    protected void prepareInitialize() {
        if (root != null) {
            root.sceneProperty().addListener((ov, oldValue, newValue) -> {
                if (oldValue == null && newValue != null) {
                    model._activate();
                    activate();
                } else if (oldValue != null && newValue == null) {
                    model._deactivate();
                    deactivate();
                }
            });
        }
    }
}
