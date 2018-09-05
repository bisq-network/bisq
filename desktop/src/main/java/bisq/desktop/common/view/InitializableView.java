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

import javafx.fxml.Initializable;

import javafx.scene.Node;

import java.net.URL;

import java.util.ResourceBundle;

public abstract class InitializableView<R extends Node, M> extends AbstractView<R, M> implements Initializable {

    public InitializableView(M model) {
        super(model);
    }

    public InitializableView() {
        this(null);
    }

    @Override
    public final void initialize(URL location, ResourceBundle resources) {
        prepareInitialize();
        initialize();
    }

    protected void prepareInitialize() {
    }

    protected void initialize() {
    }
}
