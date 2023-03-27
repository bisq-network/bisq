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

package bisq.desktop.common.fxml;

import bisq.desktop.common.ViewfxException;
import bisq.desktop.common.view.AbstractView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewFactory;
import bisq.desktop.common.view.ViewLoader;

import javafx.fxml.LoadException;

import java.util.ResourceBundle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// TODO Some refactorings seem to have broken those tests. Investigate and remove @Ignore as soon its fixed.
@Disabled
public class FxmlViewLoaderTests {

    private ViewLoader viewLoader;
    private ViewFactory viewFactory;

    @BeforeEach
    public void setUp() {
        viewFactory = mock(ViewFactory.class);
        ResourceBundle resourceBundle = mock(ResourceBundle.class);
        viewLoader = new FxmlViewLoader(viewFactory, resourceBundle);
    }


    @FxmlView
    public static class WellFormed extends AbstractView {
    }

    @Test
    public void wellFormedFxmlFileShouldSucceed() {
        given(viewFactory.call(WellFormed.class)).willReturn(new WellFormed());
        View view = viewLoader.load(WellFormed.class);
        assertThat(view, instanceOf(WellFormed.class));
    }


    @FxmlView
    public static class MissingFxController extends AbstractView {
    }

    @Test
    public void fxmlFileMissingFxControllerAttributeShouldThrow() {
        Throwable exception = assertThrows(ViewfxException.class, () -> viewLoader.load(MissingFxController.class));
        assertEquals("Does it declare an fx:controller attribute?", exception.getMessage());
    }


    public static class MissingFxmlViewAnnotation extends AbstractView {
    }

    @Test
    public void fxmlViewAnnotationShouldBeOptional() {
        given(viewFactory.call(MissingFxmlViewAnnotation.class)).willReturn(new MissingFxmlViewAnnotation());
        View view = viewLoader.load(MissingFxmlViewAnnotation.class);
        assertThat(view, instanceOf(MissingFxmlViewAnnotation.class));
    }


    @FxmlView
    public static class Malformed extends AbstractView {
    }

    @Test
    public void malformedFxmlFileShouldThrow() {
        Throwable exception = assertThrows(ViewfxException.class, () -> viewLoader.load(Malformed.class));
        assertTrue(exception.getCause() instanceof LoadException);
        assertEquals("Failed to load view from FXML file", exception.getMessage());
    }


    @FxmlView
    public static class MissingFxmlFile extends AbstractView {
    }

    @Test
    public void missingFxmlFileShouldThrow() {
        Throwable exception = assertThrows(ViewfxException.class, () -> viewLoader.load(MissingFxmlFile.class));
        assertEquals("Does it exist?", exception.getMessage());
    }


    @FxmlView(location = "unconventionally/located.fxml")
    public static class CustomLocation extends AbstractView {
    }

    @Test
    public void customFxmlFileLocationShouldOverrideDefaultConvention() {
        Throwable exception = assertThrows(ViewfxException.class, () -> viewLoader.load(CustomLocation.class));
        assertTrue(exception.getMessage().contains("Failed to load view class"));
        assertTrue(exception.getMessage().contains("CustomLocation"));
        assertTrue(exception.getMessage().contains("[unconventionally/located.fxml] could not be loaded"));
    }
}

