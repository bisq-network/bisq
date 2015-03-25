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

package io.bitsquare.offer;

import io.bitsquare.BitsquareModule;

import com.google.inject.Singleton;

import org.springframework.core.env.Environment;

public abstract class OfferModule extends BitsquareModule {

    protected OfferModule(Environment env) {
        super(env);
    }

    @Override
    protected final void configure() {
        bind(OfferBook.class).in(Singleton.class);
        bind(OfferBook.class).in(Singleton.class);

        doConfigure();
    }

    protected void doConfigure() {
    }
}
