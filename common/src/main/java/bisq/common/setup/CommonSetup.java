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

package bisq.common.setup;

import bisq.common.UserThread;
import bisq.common.crypto.LimitedKeyStrengthException;
import bisq.common.util.Utilities;

import org.bitcoinj.store.BlockStoreException;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.security.NoSuchAlgorithmException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommonSetup {

    public static void setup(UncaughtExceptionHandler uncaughtExceptionHandler) {
        setupErrorHandler(uncaughtExceptionHandler);

        if (Utilities.isLinux())
            System.setProperty("prism.lcdtext", "false");
    }

    private static void setupErrorHandler(UncaughtExceptionHandler uncaughtExceptionHandler) {
        Thread.UncaughtExceptionHandler handler = (thread, throwable) -> {
            // Might come from another thread
            if (throwable.getCause() != null && throwable.getCause().getCause() != null &&
                    throwable.getCause().getCause() instanceof BlockStoreException) {
                log.error(throwable.getMessage());
            } else if (throwable instanceof ClassCastException &&
                    "sun.awt.image.BufImgSurfaceData cannot be cast to sun.java2d.xr.XRSurfaceData".equals(throwable.getMessage())) {
                log.warn(throwable.getMessage());
            } else {
                log.error("Uncaught Exception from thread " + Thread.currentThread().getName());
                log.error("throwableMessage= " + throwable.getMessage());
                log.error("throwableClass= " + throwable.getClass());
                log.error("Stack trace:\n" + ExceptionUtils.getStackTrace(throwable));
                throwable.printStackTrace();
                UserThread.execute(() -> uncaughtExceptionHandler.handleUncaughtException(throwable, false));
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread.currentThread().setUncaughtExceptionHandler(handler);

        try {
            Utilities.checkCryptoPolicySetup();
        } catch (NoSuchAlgorithmException | LimitedKeyStrengthException e) {
            e.printStackTrace();
            UserThread.execute(() -> uncaughtExceptionHandler.handleUncaughtException(e, true));
        }
    }
}
