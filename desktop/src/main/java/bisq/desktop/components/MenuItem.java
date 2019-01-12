/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.components;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.View;

import com.jfoenix.controls.JFXButton;

import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;

import javafx.geometry.Pos;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

@Slf4j
public class MenuItem extends JFXButton implements Toggle {
    private final Navigation navigation;
    private final ObjectProperty<ToggleGroup> toggleGroupProperty = new SimpleObjectProperty<>();
    private final Class<? extends View> viewClass;
    private final List<Class<? extends View>> baseNavPath;
    private final BooleanProperty selectedProperty = new SimpleBooleanProperty();
    private final ChangeListener<Toggle> listener;

    public MenuItem(Navigation navigation,
                    ToggleGroup toggleGroup,
                    String title,
                    Class<? extends View> viewClass,
                    List<Class<? extends View>> baseNavPath) {
        this.navigation = navigation;
        this.viewClass = viewClass;
        this.baseNavPath = baseNavPath;

        setLabelText(title);
        setPrefHeight(40);
        setPrefWidth(240);
        setAlignment(Pos.CENTER_LEFT);

        toggleGroupProperty.set(toggleGroup);
        toggleGroup.getToggles().add(this);

        setUserData(getUid());

        listener = (observable, oldValue, newValue) -> {
            Object userData = newValue.getUserData();
            String uid = getUid();
            if (newValue.isSelected() && userData != null && userData.equals(uid)) {
                getStyleClass().add("action-button");
            } else {
                getStyleClass().remove("action-button");
            }
        };

    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Toggle implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ToggleGroup getToggleGroup() {
        return toggleGroupProperty.get();
    }

    @Override
    public void setToggleGroup(ToggleGroup toggleGroup) {
        toggleGroupProperty.set(toggleGroup);
    }

    @Override
    public ObjectProperty<ToggleGroup> toggleGroupProperty() {
        return toggleGroupProperty;
    }

    @Override
    public boolean isSelected() {
        return selectedProperty.get();
    }

    @Override
    public BooleanProperty selectedProperty() {
        return selectedProperty;
    }

    @Override
    public void setSelected(boolean selected) {
        selectedProperty.set(selected);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void activate() {
        setOnAction((event) -> navigation.navigateTo(getNavPathClasses()));
        toggleGroupProperty.get().selectedToggleProperty().addListener(listener);
    }

    public void deactivate() {
        setOnAction(null);
        toggleGroupProperty.get().selectedToggleProperty().removeListener(listener);
    }

    public void setLabelText(String value) {
        setText(value.toUpperCase());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    @NotNull
    private Class<? extends View>[] getNavPathClasses() {
        List<Class<? extends View>> list = new ArrayList<>(baseNavPath);
        list.add(viewClass);
        //noinspection unchecked
        Class<? extends View>[] array = new Class[list.size()];
        list.toArray(array);
        return array;
    }

    private String getUid() {
        return viewClass.getName();
    }
}
