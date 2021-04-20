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

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class CachingViewLoader implements ViewLoader {

    private final Map<Class<? extends View>, View> cache = new HashMap<>();
    private final ViewLoader viewLoader;

    @Inject
    public CachingViewLoader(ViewLoader viewLoader) {
        this.viewLoader = viewLoader;
    }

    @Override
    public View load(Class<? extends View> viewClass) {
        if (cache.containsKey(viewClass))
            return cache.get(viewClass);

        View view = viewLoader.load(viewClass);
        cache.put(viewClass, view);
        return view;
    }

    public void removeFromCache(Class<? extends View> viewClass) {
        cache.remove(viewClass);
    }
}
