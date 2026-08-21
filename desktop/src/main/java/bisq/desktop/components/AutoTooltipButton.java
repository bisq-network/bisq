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

package bisq.desktop.components;

import bisq.desktop.components.controls.BisqJfxButton;
import bisq.desktop.components.controls.skin.BisqButtonSkin;

import javafx.scene.Node;
import javafx.scene.control.Skin;

import java.util.Locale;

import static bisq.desktop.components.TooltipUtil.showTooltipIfTruncated;

public class AutoTooltipButton extends BisqJfxButton {

    public AutoTooltipButton() {
        super();
    }

    public AutoTooltipButton(String text) {
        super(text.toUpperCase(Locale.ROOT));
    }

    public AutoTooltipButton(String text, Node graphic) {
        super(text.toUpperCase(Locale.ROOT), graphic);
    }

    public void updateText(String text) {
        setText(text.toUpperCase(Locale.ROOT));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AutoTooltipButtonSkin(this);
    }

    private class AutoTooltipButtonSkin extends BisqButtonSkin {
        public AutoTooltipButtonSkin(BisqJfxButton button) {
            super(button);
        }

        @Override
        protected void layoutChildren(double x, double y, double w, double h) {
            super.layoutChildren(x, y, w, h);
            showTooltipIfTruncated(this, getSkinnable());
        }
    }
}
