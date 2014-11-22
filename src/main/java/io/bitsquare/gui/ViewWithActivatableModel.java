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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * If caching is used for loader we use the CachedViewController for turning the controller into sleep mode if not
 * active and awake it at reactivation.
 */
public abstract class ViewWithActivatableModel<M extends Activatable> extends View<M> {
    private static final Logger log = LoggerFactory.getLogger(ViewWithActivatableModel.class);

    public ViewWithActivatableModel(M model) {
        super(checkNotNull(model, "Model must not be null"));
    }

    public ViewWithActivatableModel() {
        this((M) Activatable.NOOP_INSTANCE);
    }


    /**
     * Used to activate resources (adding listeners, starting timers or animations,...)
     */
    @Override
    public final void activate() {
        model.activate();
        this.doActivate();
    }

    protected void doActivate() {
    }

    /**
     * Used for deactivating resources (removing listeners, stopping timers or animations,...)
     */
    @Override
    public final void deactivate() {
        model.deactivate();
        this.doDeactivate();
    }

    protected void doDeactivate() {
    }

}
