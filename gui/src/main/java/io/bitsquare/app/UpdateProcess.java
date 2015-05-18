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

package io.bitsquare.app;

import io.bitsquare.util.Utilities;

import com.google.inject.Inject;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.List;
import java.util.Timer;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vinumeris.updatefx.Crypto;
import com.vinumeris.updatefx.UpdateFX;
import com.vinumeris.updatefx.UpdateSummary;
import com.vinumeris.updatefx.Updater;
import org.bouncycastle.math.ec.ECPoint;
import org.springframework.core.env.Environment;
import rx.Observable;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

public class UpdateProcess {
    private static final Logger log = LoggerFactory.getLogger(UpdateProcess.class);

    private static final List<ECPoint> UPDATE_SIGNING_KEYS = Crypto.decode("038396415C265C59042AB05A5436356E8D0FA19F13E3DE4915AFF763CB4785345E");
    private static final String UPDATES_BASE_URL = "http://bitsquare.io/updateFX/";
    private static final int UPDATE_SIGNING_THRESHOLD = 1;
    private static final Path ROOT_CLASS_PATH = UpdateFX.findCodePath(BitsquareAppMain.class);

    private final Environment environment;

    public enum State {
        CHECK_FOR_UPDATES,
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        FAILURE
    }

    public final ObjectProperty<State> state = new SimpleObjectProperty<>(State.CHECK_FOR_UPDATES);

    protected String errorMessage;
    protected final Subject<State, State> process = BehaviorSubject.create();
    protected Timer timeoutTimer;

    @Inject
    public UpdateProcess(BitsquareEnvironment environment) {
        this.environment = environment;
    }

    public void restart() {
        UpdateFX.restartApp();
    }

    public Observable<State> getProcess() {
        return process.asObservable();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void init() {
        log.info("UpdateFX current version " + Version.PATCH_VERSION);

        // process.timeout() will cause an error state back but we don't want to break startup in case of an timeout
        timeoutTimer = Utilities.setTimeout(10000, () -> process.onCompleted());

        String agent = environment.getProperty(BitsquareEnvironment.APP_NAME_KEY) + Version.VERSION;

        // We use the outer dir not the app data dir including version and btc network
        Path dataDirPath = Paths.get(environment.getProperty(BitsquareEnvironment.USER_DATA_DIR_KEY),
                environment.getProperty(BitsquareEnvironment.APP_NAME_KEY));
        
        Updater updater = new Updater(UPDATES_BASE_URL, agent, Version.PATCH_VERSION, dataDirPath, ROOT_CLASS_PATH,
                UPDATE_SIGNING_KEYS, UPDATE_SIGNING_THRESHOLD) {
            @Override
            protected void updateProgress(long workDone, long max) {
                //log.trace("updateProgress " + workDone + "/" + max);
                super.updateProgress(workDone, max);
            }
        };

       /* updater.progressProperty().addListener((observableValue, oldValue, newValue) -> {
            log.trace("progressProperty newValue = " + newValue);
        });*/

        log.info("Checking for updates!");
        updater.setOnSucceeded(event -> {
            try {
                UpdateSummary summary = updater.get();
                log.info("summary " + summary.toString());
                if (summary.descriptions != null && summary.descriptions.size() > 0) {
                    log.info("One liner: {}", summary.descriptions.get(0).getOneLiner());
                    log.info("{}", summary.descriptions.get(0).getDescription());
                }
                if (summary.highestVersion > Version.PATCH_VERSION) {
                    log.info("UPDATE_AVAILABLE");
                    state.set(State.UPDATE_AVAILABLE);
                    // We stop the timeout and treat it not completed. 
                    // The user should click the restart button manually if there are updates available.
                    timeoutTimer.cancel();
                }
                else if (summary.highestVersion == Version.PATCH_VERSION) {
                    log.info("UP_TO_DATE");
                    state.set(State.UP_TO_DATE);
                    timeoutTimer.cancel();
                    process.onCompleted();
                }
            } catch (Throwable e) {
                log.error("Exception at processing UpdateSummary: " + e.getMessage());

                // we treat errors as update not as critical errors to prevent startup, 
                // so we use state.onCompleted() instead of state.onError()
                errorMessage = "Exception at processing UpdateSummary: " + e.getMessage();
                state.set(State.FAILURE);
                timeoutTimer.cancel();
                process.onCompleted();
            }
        });
        updater.setOnFailed(event -> {
            log.error("Update failed: " + updater.getException());
            updater.getException().printStackTrace();

            // we treat errors as update not as critical errors to prevent startup, 
            // so we use state.onCompleted() instead of state.onError()
            errorMessage = "Update failed: " + updater.getException();
            state.set(State.FAILURE);
            timeoutTimer.cancel();
            process.onCompleted();
        });

        Thread thread = new Thread(updater, "Online update check");
        thread.setDaemon(true);
        thread.start();
    }
}