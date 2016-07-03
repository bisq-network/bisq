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

package io.bitsquare.filter;

import com.google.inject.Singleton;
import io.bitsquare.app.AppModule;
import io.bitsquare.common.OptionKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import static com.google.inject.name.Names.named;

public class FilterModule extends AppModule {
    private static final Logger log = LoggerFactory.getLogger(FilterModule.class);

    public FilterModule(Environment env) {
        super(env);
    }

    @Override
    protected final void configure() {
        bind(FilterManager.class).in(Singleton.class);
        bindConstant().annotatedWith(named(OptionKeys.IGNORE_DEV_MSG_KEY)).to(env.getRequiredProperty(OptionKeys.IGNORE_DEV_MSG_KEY));
    }
}
