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

package viewfx.view.support;

import java.util.HashMap;

import javax.inject.Inject;

import viewfx.view.View;
import viewfx.view.ViewLoader;

public class CachingViewLoader implements ViewLoader {

    private final HashMap<Object, View> cache = new HashMap<>();
    private final ViewLoader delegate;

    @Inject
    public CachingViewLoader(ViewLoader delegate) {
        this.delegate = delegate;
    }

    @Override
    public View load(Class<? extends View> viewClass) {
        if (cache.containsKey(viewClass))
            return cache.get(viewClass);

        View view = delegate.load(viewClass);
        cache.put(viewClass, view);
        return view;
    }
}
