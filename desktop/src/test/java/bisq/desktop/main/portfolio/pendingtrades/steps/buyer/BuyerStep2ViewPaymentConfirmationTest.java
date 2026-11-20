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

package bisq.desktop.main.portfolio.pendingtrades.steps.buyer;

import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesDataModel;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesViewModel;

import bisq.core.locale.Res;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.handlers.ErrorMessageHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.List;

import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;



import com.sun.javafx.perf.PerformanceTracker;
import com.sun.javafx.tk.Toolkit;

/**
 * A retry of the payment confirmation leaves the state of the trade as it is, for example while the message was sent
 * and the seller has not acknowledged it. The view must offer the next retry after its timeout also without a change
 * of the state.
 */
class BuyerStep2ViewPaymentConfirmationTest {
    private MockedStatic<Toolkit> toolkit;
    private MockedStatic<UserThread> userThread;
    private MockedConstruction<Popup> popups;
    private final List<Runnable> timeouts = new ArrayList<>();
    private final List<Timer> timers = new ArrayList<>();
    private BuyerStep2View view;
    private Trade trade;
    private PendingTradesDataModel dataModel;

    @BeforeEach
    void setUp() throws Exception {
        Res.setup();
        // Exercise the real view methods without constructing controls or displaying windows.
        toolkit = mockStatic(Toolkit.class);
        Toolkit headlessToolkit = mock(Toolkit.class);
        toolkit.when(Toolkit::getToolkit).thenReturn(headlessToolkit);
        when(headlessToolkit.getPerformanceTracker()).thenReturn(mock(PerformanceTracker.class));
        userThread = mockStatic(UserThread.class);
        userThread.when(() -> UserThread.runAfter(any(Runnable.class), anyLong())).thenAnswer(call -> {
            timeouts.add(call.getArgument(0));
            Timer timer = mock(Timer.class);
            timers.add(timer);
            return timer;
        });
        popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF));

        view = mock(BuyerStep2View.class, CALLS_REAL_METHODS);
        // The status shows on controls, which need a running toolkit
        doNothing().when(view).showStatus(anyBoolean(), anyString());
        trade = mock(Trade.class);
        dataModel = mock(PendingTradesDataModel.class);
        PendingTradesViewModel model = mock(PendingTradesViewModel.class);
        setField(model, "dataModel", dataModel);
        setField(view, "model", model);
        setField(view, "trade", trade);
    }

    @AfterEach
    void tearDown() {
        if (popups != null)
            popups.close();
        if (userThread != null)
            userThread.close();
        if (toolkit != null)
            toolkit.close();
    }

    @Test
    void aConfirmationOffersTheNextRetryAfterTheTimeout() throws Exception {
        confirmPaymentStarted();

        verify(dataModel).onPaymentStarted(eq(trade), any(), any());
        verify(view).showStatus(true, Res.get("shared.sendingConfirmation"));
        userThread.verify(() -> UserThread.runAfter(any(Runnable.class), eq(10L)));
        assertEquals(1, timeouts.size());

        timeouts.get(0).run();

        verify(view).showStatus(false, Res.get("shared.sendingConfirmationAgain"));
    }

    @Test
    void aRetryReplacesTheTimeoutOfTheEarlierConfirmation() throws Exception {
        confirmPaymentStarted();
        confirmPaymentStarted();

        assertEquals(2, timers.size());
        verify(timers.get(0)).stop();
        verify(timers.get(1), never()).stop();
        verify(dataModel, times(2)).onPaymentStarted(eq(trade), any(), any());
    }

    @Test
    void aRefusedConfirmationOffersNoRetry() throws Exception {
        doAnswer(call -> {
            call.<ErrorMessageHandler>getArgument(2).handleErrorMessage("refused");
            return null;
        }).when(dataModel).onPaymentStarted(eq(trade), any(), any());

        confirmPaymentStarted();

        verify(timers.get(0)).stop();
        verify(view).showStatus(false, "");
        assertEquals(1, popups.constructed().size());
        verify(popups.constructed().get(0)).warning(Res.get("popup.warning.sendMsgFailed"));
    }

    private void confirmPaymentStarted() throws Exception {
        Method method = BuyerStep2View.class.getDeclaredMethod("confirmPaymentStarted");
        method.setAccessible(true);
        method.invoke(view);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // Mockito subclasses and the view model inherit their state fields.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
