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

import org.springframework.core.env.Environment;

public abstract class MessageModule extends BitsquareModule {

    protected MessageModule(Environment env) {
        super(env);
    }

    @Override
    protected final void configure() {
        bind(MessageService.class).to(messageService()).asEagerSingleton();

        doConfigure();
    }

    protected void doConfigure() {
    }

    protected abstract Class<? extends MessageService> messageService();

    @Override
    protected void doClose(Injector injector) {
        injector.getInstance(MessageService.class).shutDown();
    }
}
