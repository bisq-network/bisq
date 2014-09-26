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

package io.bitsquare.gui.components.processbar;

import java.util.List;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.*;

public class ProcessStepBar<T> extends Control {

    private List<ProcessStepItem> processStepItems;
    private final IntegerProperty selectedIndex = new SimpleIntegerProperty(0);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ProcessStepBar() {
    }

    public ProcessStepBar(List<ProcessStepItem> processStepItems) {
        this.processStepItems = processStepItems;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void next() {
        setSelectedIndex(getSelectedIndex() + 1);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ProcessStepBarSkin<>(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setProcessStepItems(List<ProcessStepItem> processStepItems) {
        this.processStepItems = processStepItems;
        if (getSkin() != null)
            ((ProcessStepBarSkin) getSkin()).setProcessStepItems(processStepItems);
    }

    public void setSelectedIndex(int selectedIndex) {
        this.selectedIndex.set(selectedIndex);
        if (getSkin() != null)
            ((ProcessStepBarSkin) getSkin()).setSelectedIndex(selectedIndex);
    }

    public void reset() {
        if (getSkin() != null)
            ((ProcessStepBarSkin) getSkin()).reset();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////
    public List<ProcessStepItem> getProcessStepItems() {
        return processStepItems;
    }

    public int getSelectedIndex() {
        return selectedIndex.get();
    }

    public IntegerProperty selectedIndexProperty() {
        return selectedIndex;
    }


}
