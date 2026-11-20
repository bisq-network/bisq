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

package bisq.desktop.main.overlays;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OverlayTest {

    @Test
    public void typeSafeCreation() {
        new A();
        new C();
        new D<>();
    }

    @Test
    public void typeUnsafeCreation() {
        assertThrows(RuntimeException.class, B::new);
    }

    @Test
    public void hyperlinksAreReplacedByReferences() {
        List<String> hyperlinks = new ArrayList<>();

        String text = Overlay.extractHyperlinks("See [HYPERLINK:https://bisq.wiki] and [HYPERLINK:https://bisq.network].",
                hyperlinks);

        assertEquals("See [1] and [2].", text);
        assertEquals(List.of("https://bisq.wiki", "https://bisq.network"), hyperlinks);
    }

    private static class A extends Overlay<A> {
    }

    private static class B extends Overlay<A> {
    }

    private static class C extends TabbedOverlay<C> {
    }

    private static class D<T> extends Overlay<D<T>> {
    }
}
