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

package io.bitsquare.gui;

import java.net.URL;

import java.util.ResourceBundle;

import javafx.fxml.Initializable;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class Controller<M extends Model> implements Initializable {

    public static final String TITLE_KEY = "view.title";

    protected M model;

    protected Controller(M model) {
        this.model = checkNotNull(model);
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        model.initialize();
        doInitialize();
    }

    public final void terminate() {
        //model.terminate();
        //doTerminate();
    }

    protected abstract void doInitialize();

    protected void doTerminate() { };
}
