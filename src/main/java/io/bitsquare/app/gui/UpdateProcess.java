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

package io.bitsquare.app.gui;

import io.bitsquare.app.BitsquareEnvironment;
import io.bitsquare.util.Utilities;

import com.google.inject.Inject;

import java.io.File;

import java.nio.file.Path;

import java.util.List;
import java.util.function.Function;

import javafx.animation.AnimationTimer;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

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

    private static final int VERSION = 1;
    private static final List<ECPoint> UPDATE_SIGNING_KEYS = Crypto.decode(
            "028B41BDDCDCAD97B6AE088FEECA16DC369353B717E13319370C729CB97D677A11",
            "031E3D80F21A4D10D385A32ABEDC300DACBEDBC839FBA58376FBD5D791D806BA68"
    );
    private static final int UPDATE_SIGNING_THRESHOLD = 1;
    private static final String UPDATES_BASE_URL = "http://localhost:8000/";
    private static final Path ROOT_CLASS_PATH = UpdateFX.findCodePath(BitsquareAppMain.class);


    public enum State {
        CHECK_FOR_UPDATES,
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        FAILURE
    }

    public final ObjectProperty<State> state = new SimpleObjectProperty<>(State.CHECK_FOR_UPDATES);

    protected String errorMessage;
    protected final Subject<State, State> process = BehaviorSubject.create();
    protected final AnimationTimer timeoutTimer;

    @Inject
    public UpdateProcess(Environment environment) {
        // process.timeout() will cause an error state back but we dont want to break startup in case of an update 
        // timeout 
        timeoutTimer = Utilities.setTimeout(10000, new Function<AnimationTimer, Void>() {
            @Override
            public Void apply(AnimationTimer animationTimer) {
                process.onCompleted();
                return null;
            }
        });
        timeoutTimer.start();

        init(environment);
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

    protected void init(Environment environment) {
        log.info("version " + VERSION);

        String agent = environment.getProperty(BitsquareEnvironment.APP_NAME_KEY) + VERSION;
        Path dataDirPath = new File(environment.getProperty(BitsquareEnvironment.APP_DATA_DIR_KEY)).toPath();
        Updater updater = new Updater(UPDATES_BASE_URL, agent, VERSION, dataDirPath, ROOT_CLASS_PATH,
                UPDATE_SIGNING_KEYS, UPDATE_SIGNING_THRESHOLD) {
            @Override
            protected void updateProgress(long workDone, long max) {
                log.debug("updateProgress " + workDone + "/" + max);
                super.updateProgress(workDone, max);
            }
        };

        updater.progressProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number oldValue, Number newValue) {
                log.trace("progressProperty newValue = " + newValue);
            }
        });

        log.info("Checking for updates!");
        updater.setOnSucceeded(event -> {
            try {
                UpdateSummary summary = updater.get();
                if (summary.descriptions.size() > 0) {
                    log.info("One liner: {}", summary.descriptions.get(0).getOneLiner());
                    log.info("{}", summary.descriptions.get(0).getDescription());
                }
                if (summary.highestVersion > VERSION) {
                    state.set(State.UPDATE_AVAILABLE);
                }
                else if (summary.highestVersion == VERSION) {
                    state.set(State.UP_TO_DATE);
                    timeoutTimer.stop();
                    process.onCompleted();
                }
                
               /* if (summary.highestVersion > VERSION) {
                    log.info("Restarting to get version " + summary.highestVersion);
                    if (UpdateFX.getVersionPin(dataDirPath) == 0)
                        UpdateFX.restartApp();
                }*/
            } catch (Throwable e) {
                log.error("Exception at processing UpdateSummary: " + e.getMessage());

                // we treat errors as update not as critical errors to prevent startup, 
                // so we use state.onCompleted() instead of state.onError()
                errorMessage = "Exception at processing UpdateSummary: " + e.getMessage();
                state.set(State.FAILURE);
                timeoutTimer.stop();
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
            timeoutTimer.stop();
            process.onCompleted();
        });

        Thread thread = new Thread(updater, "Online update check");
        thread.setDaemon(true);
        thread.start();
    }

}
