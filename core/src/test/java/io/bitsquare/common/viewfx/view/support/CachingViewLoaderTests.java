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

package io.bitsquare.common.viewfx.view.support;

import io.bitsquare.common.viewfx.view.AbstractView;
import io.bitsquare.common.viewfx.view.CachingViewLoader;
import io.bitsquare.common.viewfx.view.ViewLoader;

import org.junit.Test;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

public class CachingViewLoaderTests {

    @Test
    public void test() {
        ViewLoader delegateViewLoader = mock(ViewLoader.class);

        ViewLoader cachingViewLoader = new CachingViewLoader(delegateViewLoader);

        cachingViewLoader.load(TestView1.class);
        cachingViewLoader.load(TestView1.class);
        cachingViewLoader.load(TestView2.class);

        then(delegateViewLoader).should(times(1)).load(TestView1.class);
        then(delegateViewLoader).should(times(1)).load(TestView2.class);
        then(delegateViewLoader).should(times(0)).load(TestView3.class);
    }


    static class TestView1 extends AbstractView {
    }

    static class TestView2 extends AbstractView {
    }

    static class TestView3 extends AbstractView {
    }
}