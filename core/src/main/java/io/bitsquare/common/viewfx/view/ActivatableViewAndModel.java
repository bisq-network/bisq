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

package io.bitsquare.common.viewfx.view;

import io.bitsquare.common.viewfx.model.Activatable;

import javafx.scene.*;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class ActivatableViewAndModel<R extends Node, M extends Activatable> extends ActivatableView<R, M> {

    public ActivatableViewAndModel(M model) {
        super(checkNotNull(model, "Model must not be null"));
    }

    public ActivatableViewAndModel() {
        this((M) Activatable.NOOP_INSTANCE);
    }

    @Override
    public final void activate() {
        model.activate();
        this.doActivate();
    }

    protected void doActivate() {
    }

    @Override
    public final void deactivate() {
        model.deactivate();
        this.doDeactivate();
    }

    protected void doDeactivate() {
    }
}
