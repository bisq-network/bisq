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

package io.bitsquare;

import com.google.common.collect.Sets;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;

import java.util.Properties;
import java.util.Set;

public abstract class AbstractBitsquareModule extends AbstractModule {

    protected final Properties properties;

    private final Set<AbstractBitsquareModule> modules = Sets.newHashSet();

    public AbstractBitsquareModule(Properties properties) {
        this.properties = properties;
    }

    protected void install(AbstractBitsquareModule module) {
        super.install(module);
        modules.add(module);
    }

    /**
     * Close any instances this module is responsible for and recursively close any
     * sub-modules installed via {@link #install(AbstractBitsquareModule)}. This method
     * must be called manually, e.g. at the end of a main() method or in the stop() method
     * of a JavaFX Application; alternatively it may be registered as a JVM shutdown hook.
     *
     * @param injector the Injector originally initialized with this module
     * @see #doClose(com.google.inject.Injector)
     */
    public final void close(Injector injector) {
        modules.forEach(module -> module.close(injector));
        doClose(injector);
    }

    /**
     * Actually perform closing of any instances this module is responsible for. Called by
     * {@link #close(Injector)}.
     *
     * @param injector the Injector originally initialized with this module
     */
    protected void doClose(Injector injector) {
    }
}
