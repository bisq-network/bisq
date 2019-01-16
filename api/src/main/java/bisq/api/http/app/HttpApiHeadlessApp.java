package bisq.api.http.app;

import bisq.core.app.BisqHeadlessApp;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.setup.UncaughtExceptionHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * BisqHeadlessApp implementation for HttpApi.
 * This is only used in case of the headless version to startup Bisq.
 */
@Slf4j
class HttpApiHeadlessApp extends BisqHeadlessApp implements UncaughtExceptionHandler {

    @Override
    protected void setupHandlers() {
        super.setupHandlers();

        bisqSetup.setRequestWalletPasswordHandler(aesKeyHandler -> {
            log.info("onRequestWalletPasswordHandler");

            // Add a periodic log so that users get reminded to enter the pw
            Timer reminder = UserThread.runPeriodically(() -> {
                log.info("Awaiting user's wallet password to be entered via API call");
            }, 10);


            // TODO @bernard listen for users input of pw, create aseKey and call handler
            // aesKeyHandler.accept(aseKey);
            // Once pw is entered we stop periodic log
            // reminder.stop();


            // here is code from UI
           /* String password = passwordTextField.getText();
            checkArgument(password.length() < 500, Res.get("password.tooLong"));
            KeyCrypterScrypt keyCrypterScrypt = walletsManager.getKeyCrypterScrypt();
            if (keyCrypterScrypt != null) {
                busyAnimation.play();
                deriveStatusLabel.setText(Res.get("password.deriveKey"));
                ScryptUtil.deriveKeyWithScrypt(keyCrypterScrypt, password, aesKey -> {
                    if (walletsManager.checkAESKey(aesKey)) {
                        if (aesKeyHandler != null)
                            aesKeyHandler.onAesKey(aesKey);

                        hide();
                    } else {
                        busyAnimation.stop();
                        deriveStatusLabel.setText("");

                        UserThread.runAfter(() -> new Popup<>()
                                .warning(Res.get("password.wrongPw"))
                                .onClose(this::blurAgain).show(), Transitions.DEFAULT_DURATION, TimeUnit.MILLISECONDS);
                    }
                });
            } else {
                log.error("wallet.getKeyCrypter() is null, that must not happen.");
            }
            */
        });
    }
}
