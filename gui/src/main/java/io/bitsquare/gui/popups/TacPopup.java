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

package io.bitsquare.gui.popups;

import io.bitsquare.app.BitsquareApp;
import io.bitsquare.common.util.Tuple2;
import javafx.scene.control.Button;

import java.util.Optional;

import static io.bitsquare.gui.util.FormBuilder.add2ButtonsAfterGroup;

public class TacPopup extends WebViewPopup {

    private Optional<Runnable> agreeHandlerOptional;

    public TacPopup onAgree(Runnable agreeHandler) {
        this.agreeHandlerOptional = Optional.of(agreeHandler);
        return this;
    }

    @Override
    public TacPopup url(String url) {
        super.url(url);
        return this;
    }

    @Override
    protected void addHtmlContent() {
        super.addHtmlContent();

        Tuple2<Button, Button> tuple = add2ButtonsAfterGroup(gridPane, ++rowIndex, "I agree", "Quit");
        Button agreeButton = tuple.first;
        Button quitButton = tuple.second;

        agreeButton.setOnAction(e -> {
            agreeHandlerOptional.ifPresent(agreeHandler -> agreeHandler.run());
            hide();
        });
        quitButton.setOnAction(e -> BitsquareApp.shutDownHandler.run());
    }
}
