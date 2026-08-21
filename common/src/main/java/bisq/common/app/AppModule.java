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

package bisq.common.app;

import bisq.common.config.Config;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;

import java.util.ArrayList;
import java.util.List;

public abstract class AppModule extends AbstractModule {

    protected final Config config;

    private final List<AppModule> modules = new ArrayList<>();

    protected AppModule(Config config) {
        this.config = config;
    }

    protected void install(AppModule module) {
        super.install(module);
        modules.add(module);
    }

    /**
     * Close any instances this module is responsible for and recursively close any
     * sub-modules installed via {@link #install(AppModule)}. This method
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
    @SuppressWarnings({"WeakerAccess", "EmptyMethod", "UnusedParameters"})
    protected void doClose(Injector injector) {
    }
}
