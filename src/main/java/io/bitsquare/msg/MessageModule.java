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

package io.bitsquare.msg;

import io.bitsquare.BitsquareModule;

import com.google.inject.Injector;

import java.util.Properties;

public abstract class MessageModule extends BitsquareModule {

    protected MessageModule(Properties properties) {
        super(properties);
    }

    @Override
    protected final void configure() {
        bind(MessageFacade.class).to(messageFacade()).asEagerSingleton();

        doConfigure();
    }

    protected void doConfigure() {
    }

    protected abstract Class<? extends MessageFacade> messageFacade();

    @Override
    protected void doClose(Injector injector) {
        injector.getInstance(MessageFacade.class).shutDown();
    }
}
