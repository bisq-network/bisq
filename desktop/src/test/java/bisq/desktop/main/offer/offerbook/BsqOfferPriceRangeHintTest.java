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

package bisq.desktop.main.offer.offerbook;

import bisq.desktop.Navigation;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.popups.PopupManager;

import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;

import bisq.common.Timer;
import bisq.common.UserThread;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class BsqOfferPriceRangeHintTest {
    private final List<Popup> blockingPopups = new ArrayList<>();
    private MockedStatic<UserThread> userThread;
    private MockedConstruction<Popup> popups;
    private BsqOfferPriceRangeHint hint;
    private Runnable hintTimer;
    private Runnable displayNext;
    private boolean applicable = true;

    @BeforeEach
    void setUp() {
        Res.setup();
        userThread = mockStatic(UserThread.class);
        userThread.when(() -> UserThread.runAfter(any(Runnable.class), anyLong())).thenAnswer(invocation -> {
            hintTimer = invocation.getArgument(0);
            return mock(Timer.class);
        });
        userThread.when(() -> UserThread.runAfter(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    displayNext = invocation.getArgument(0);
                    return mock(Timer.class);
                });
        popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF));
        Preferences preferences = mock(Preferences.class);
        when(preferences.getBsqOfferPriceRangeFactor()).thenReturn(2d);
        hint = new BsqOfferPriceRangeHint(preferences, mock(Navigation.class), () -> applicable);
    }

    @AfterEach
    void tearDown() {
        hint.reset();
        if (displayNext != null) {
            displayNext.run();
        }
        for (Popup popup : blockingPopups) {
            if (PopupManager.isDisplayedOrQueued(popup)) {
                PopupManager.onHidden(popup);
                displayNext.run();
            }
        }
        popups.close();
        userThread.close();
    }

    @Test
    void hintWaitsForAnotherPopupAndRechecksOffers() {
        Popup blocker = showBlockingPopup();
        hint.update();
        hintTimer.run();

        assertTrue(popups.constructed().isEmpty());

        applicable = false;
        hint.update();
        PopupManager.onHidden(blocker);
        displayNext.run();
        hintTimer.run();

        assertTrue(popups.constructed().isEmpty());
    }

    @Test
    void hintWaitsDuringTheTransitionBetweenQueuedPopups() {
        Popup blocker = showBlockingPopup();
        showBlockingPopup();
        PopupManager.onHidden(blocker);

        // The next queued popup has not been displayed yet.
        assertTrue(PopupManager.isNoPopupDisplayed());
        hint.update();
        hintTimer.run();

        assertTrue(popups.constructed().isEmpty());
    }

    @Test
    void hintAppearsOnceWhenThePopupQueueClears() {
        Popup blocker = showBlockingPopup();
        hint.update();
        hintTimer.run();
        assertTrue(popups.constructed().isEmpty());

        PopupManager.onHidden(blocker);
        displayNext.run();
        hintTimer.run();

        assertEquals(1, popups.constructed().size());
        verify(popups.constructed().get(0)).show();

        hint.update();
        hintTimer.run();
        assertEquals(1, popups.constructed().size());
    }

    // Invented trade statistics can move the average so far that every factor below the largest one hides the offers at
    // the market price, and the preferences do not say that the largest factor turns the range off
    @Test
    void hintNamesTheFactorWhichShowsAllOffers() {
        Locale previousLocale = GlobalSettings.getLocale();
        // The translations do not name it yet
        GlobalSettings.setLocale(Locale.ENGLISH);
        try {
            hint.update();
            hintTimer.run();
        } finally {
            GlobalSettings.setLocale(previousLocale);
        }

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(popups.constructed().get(0)).information(message.capture());
        String largestFactor = BigDecimal.valueOf(Preferences.MAX_BSQ_OFFER_PRICE_RANGE_FACTOR).stripTrailingZeros()
                .toPlainString();
        assertTrue(message.getValue().contains(largestFactor), message.getValue());
    }

    private Popup showBlockingPopup() {
        Popup popup = mock(Popup.class);
        blockingPopups.add(popup);
        PopupManager.queueForDisplay(popup);
        return popup;
    }
}
