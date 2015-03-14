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

package io.bitsquare.viewfx.view.fxml;

import io.bitsquare.viewfx.ViewfxException;
import io.bitsquare.viewfx.view.AbstractView;
import io.bitsquare.viewfx.view.FxmlView;
import io.bitsquare.viewfx.view.View;
import io.bitsquare.viewfx.view.ViewFactory;
import io.bitsquare.viewfx.view.ViewLoader;

import java.util.ResourceBundle;

import javafx.fxml.LoadException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class FxmlViewLoaderTests {

    private ViewLoader viewLoader;
    private ViewFactory viewFactory;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() {
        viewFactory = mock(ViewFactory.class);
        ResourceBundle resourceBundle = mock(ResourceBundle.class);
        viewLoader = new FxmlViewLoader(viewFactory, resourceBundle);
    }


    @FxmlView
    static class WellFormed extends AbstractView {
    }

    @Test
    public void wellFormedFxmlFileShouldSucceed() {
        given(viewFactory.call(WellFormed.class)).willReturn(new WellFormed());
        View view = viewLoader.load(WellFormed.class);
        assertThat(view, instanceOf(WellFormed.class));
    }


    @FxmlView
    static class MissingFxController extends AbstractView {
    }

    @Test
    public void fxmlFileMissingFxControllerAttributeShouldThrow() {
        thrown.expect(ViewfxException.class);
        thrown.expectMessage("Does it declare an fx:controller attribute?");
        viewLoader.load(MissingFxController.class);
    }


    static class MissingFxmlViewAnnotation extends AbstractView {
    }

    @Test
    public void fxmlViewAnnotationShouldBeOptional() {
        given(viewFactory.call(MissingFxmlViewAnnotation.class)).willReturn(new MissingFxmlViewAnnotation());
        View view = viewLoader.load(MissingFxmlViewAnnotation.class);
        assertThat(view, instanceOf(MissingFxmlViewAnnotation.class));
    }


    @FxmlView
    static class Malformed extends AbstractView {
    }

    @Test
    public void malformedFxmlFileShouldThrow() {
        thrown.expect(ViewfxException.class);
        thrown.expectMessage("Failed to load view from FXML file");
        thrown.expectCause(instanceOf(LoadException.class));
        viewLoader.load(Malformed.class);
    }


    @FxmlView
    static class MissingFxmlFile extends AbstractView {
    }

    @Test
    public void missingFxmlFileShouldThrow() {
        thrown.expect(ViewfxException.class);
        thrown.expectMessage("Does it exist?");
        viewLoader.load(MissingFxmlFile.class);
    }


    @FxmlView(location = "unconventionally/located.fxml")
    static class CustomLocation extends AbstractView {
    }

    @Test
    public void customFxmlFileLocationShouldOverrideDefaultConvention() {
        thrown.expect(ViewfxException.class);
        thrown.expectMessage("Failed to load view class");
        thrown.expectMessage("CustomLocation");
        thrown.expectMessage("[unconventionally/located.fxml] could not be loaded");
        viewLoader.load(CustomLocation.class);
    }
}

