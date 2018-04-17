package bisq.desktop;

import bisq.desktop.app.BisqApp;
import bisq.desktop.setup.DesktopPersistedDataHost;

import bisq.core.app.BisqEnvironment;

import bisq.common.proto.persistable.PersistedDataHost;

import org.springframework.util.StringUtils;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;

import javafx.application.Application;

import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;
import static java.lang.String.join;



import bisq.spi.LoadableExtension;

@Slf4j
public class DesktopExtension implements LoadableExtension {
    @Override
    public void decorateOptionParser(OptionParser parser) {
        parser.accepts("gui", "Enable GUI").withRequiredArg().ofType(boolean.class).defaultsTo(true);
    }

    @Override
    public AbstractModule configure(OptionSet options) {
        final DesktopEnvironment environment = new DesktopEnvironment(options);
        return new DesktopModule(environment);
    }

    @Override
    public CompletableFuture<Void> setup(Injector injector) {
        PersistedDataHost.apply(DesktopPersistedDataHost.getPersistedDataHosts(injector));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> preStart(Injector injector) {
        final DesktopEnvironment environment = injector.getInstance(DesktopEnvironment.class);
        if (!environment.isEnabled()) {
            log.debug("DesktopExtension is disabled");
            return CompletableFuture.completedFuture(null);
        }
        final CompletableFuture<Void> future = new CompletableFuture<>();
        new Thread() {
            @Override
            public void run() {
                BisqApp.setEnvironment(injector.getInstance(BisqEnvironment.class));
                BisqApp.setInjector(injector);
                BisqApp.setOnReadyToStart(() -> future.complete(null));
//                REFACTOR main should expose some interface for shutting down and closing aggregated module
//                                BisqApp.setOnShutdownHook(() -> bisqAppModule.close(injector));
                Application.launch(BisqApp.class);
            }
        }.start();
        return future;
    }

    @Override
    public void start(Injector injector) {

    }

    private static String description(String descText, Object defaultValue) {
        String description = "";
        if (StringUtils.hasText(descText))
            description = description.concat(descText);
        if (defaultValue != null)
            description = join(" ", description, format("(default: %s)", defaultValue));
        return description;
    }
}
