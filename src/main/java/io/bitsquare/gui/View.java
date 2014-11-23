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

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class View<M> {

    public static final String TITLE_KEY = "view.title";

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    protected final M model;
    protected @FXML Parent root;

    protected Initializable childController;
    protected Initializable parent;


    public View(M model) {
        this.model = model;
    }

    public View() {
        this(null);
    }

    public void setParent(Initializable parent) {
        this.parent = parent;
    }

    protected Initializable loadView(Navigation.Item navigationItem) {
    }
}
