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

package bisq.core.trade.statistics;

import bisq.core.network.p2p.seed.DefaultSeedNodeRepository;
import bisq.core.proto.network.CoreNetworkProtoResolver;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.provider.PriceHttpClient;
import bisq.core.provider.ProvidersRepository;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;

import bisq.network.Socks5ProxyProvider;
import bisq.network.crypto.EncryptionService;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.IgnoredMailboxService;
import bisq.network.p2p.mailbox.MailboxMessageService;
import bisq.network.p2p.network.LocalhostNetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.peers.getdata.RequestDataManager;
import bisq.network.p2p.peers.keepalive.KeepAliveManager;
import bisq.network.p2p.peers.peerexchange.PeerExchangeManager;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;
import bisq.network.p2p.storage.persistence.RemovedPayloadsService;
import bisq.network.p2p.storage.persistence.ResourceDataStoreService;

import bisq.common.ClockWatcher;
import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.file.CorruptedStorageFileHandler;
import bisq.common.file.FileUtil;
import bisq.common.persistence.PersistenceManager;

import java.time.Clock;

import java.nio.file.Files;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static bisq.network.utils.Utils.findFreeSystemPort;

/**
 * P2P storage bootstrapper for testing statistics storage file read & write.
 *
 * TODO Rename, refactor, and move to different pkg when needed by other core test cases.
 */
@Slf4j
@Getter
class StatisticsTestBootstrapper {

    private final File storageDir;
    private final File dbStorageDir;
    private final File keysStorageDir;
    private final Config config;
    private final String storeFilenamePostFix = "_TEST"; // or = "_" + config.baseCurrencyNetwork.name();
    private final Clock clock;
    private final LocalhostNetworkNode networkNode;
    private final P2PDataStorage p2PDataStorage;
    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final TradeStatistics3StorageService tradeStatistics3StorageService;
    private final ApiTradeStatisticsStorageService apiTradeStatisticsStorageService;
    private final CorruptedStorageFileHandler corruptedStorageFileHandler;
    private final CoreNetworkProtoResolver coreNetworkProtoResolver;
    private final CorePersistenceProtoResolver corePersistenceProtoResolver;
    private final DefaultSeedNodeRepository seedNodeRepository;
    private final PeerManager peerManager;
    private final Broadcaster broadcaster;
    private final AppendOnlyDataStoreService appendOnlyDataStoreService;
    private final ProtectedDataStoreService protectedDataStoreService;
    private final ResourceDataStoreService resourceDataStoreService;
    private final RemovedPayloadsService removedPayloadsService;
    private final int maxSequenceNumberBeforePurge = 5;

    StatisticsTestBootstrapper() {
        this.config = new Config();
        this.clock = Clock.systemDefaultZone();
        this.storageDir = createStorageDirs();
        this.dbStorageDir = new File(storageDir.getAbsolutePath(), "db");
        this.keysStorageDir = new File(storageDir.getAbsolutePath(), "keys");
        this.corruptedStorageFileHandler = new CorruptedStorageFileHandler();
        this.coreNetworkProtoResolver = new CoreNetworkProtoResolver(clock);
        this.corePersistenceProtoResolver = new CorePersistenceProtoResolver(null, coreNetworkProtoResolver);
        this.networkNode = new LocalhostNetworkNode(findFreeSystemPort(), coreNetworkProtoResolver, null);
        this.seedNodeRepository = new DefaultSeedNodeRepository(config);
        this.peerManager = new PeerManager(networkNode,
                seedNodeRepository,
                new ClockWatcher(),
                new PersistenceManager<>(storageDir, corePersistenceProtoResolver, corruptedStorageFileHandler),
                5);
        this.broadcaster = new Broadcaster(networkNode, peerManager);
        this.protectedDataStoreService = new ProtectedDataStoreService();
        this.resourceDataStoreService = new ResourceDataStoreService();
        this.removedPayloadsService = new RemovedPayloadsService(
                new PersistenceManager<>(storageDir, corePersistenceProtoResolver, corruptedStorageFileHandler));

        this.tradeStatistics3StorageService = new TradeStatistics3StorageService(dbStorageDir,
                new PersistenceManager<>(dbStorageDir, corePersistenceProtoResolver, corruptedStorageFileHandler));
        this.apiTradeStatisticsStorageService = new ApiTradeStatisticsStorageService(dbStorageDir,
                new PersistenceManager<>(dbStorageDir, corePersistenceProtoResolver, corruptedStorageFileHandler));

        this.appendOnlyDataStoreService = new AppendOnlyDataStoreService();
        appendOnlyDataStoreService.addService(tradeStatistics3StorageService);
        appendOnlyDataStoreService.addService(apiTradeStatisticsStorageService);

        this.p2PDataStorage = new P2PDataStorage(networkNode,
                broadcaster,
                appendOnlyDataStoreService,
                protectedDataStoreService,
                resourceDataStoreService,
                new PersistenceManager<>(dbStorageDir, corePersistenceProtoResolver, corruptedStorageFileHandler),
                removedPayloadsService,
                clock,
                maxSequenceNumberBeforePurge);

        KeyRing keyRing = new KeyRing(new KeyStorage(keysStorageDir));
        RequestDataManager requestDataManager = new RequestDataManager(networkNode,
                seedNodeRepository,
                p2PDataStorage,
                peerManager);
        PeerExchangeManager peerExchangeManager = new PeerExchangeManager(networkNode,
                seedNodeRepository,
                peerManager);
        KeepAliveManager keepAliveManager = new KeepAliveManager(networkNode, peerManager);
        // config.socks5ProxyBtcAddress, config.socks5ProxyHttpAddress are null
        Socks5ProxyProvider socks5ProxyProvider = new Socks5ProxyProvider(config.socks5ProxyBtcAddress, config.socks5ProxyHttpAddress);
        EncryptionService encryptionService = new EncryptionService(keyRing, coreNetworkProtoResolver);
        MailboxMessageService mailboxMessageService = new MailboxMessageService(networkNode,
                peerManager,
                p2PDataStorage,
                encryptionService,
                new IgnoredMailboxService(new PersistenceManager<>(dbStorageDir, corePersistenceProtoResolver, corruptedStorageFileHandler)),
                new PersistenceManager<>(dbStorageDir, corePersistenceProtoResolver, corruptedStorageFileHandler),
                keyRing,
                clock,
                false);

        Preferences preferences = new Preferences(
                new PersistenceManager<>(dbStorageDir, corePersistenceProtoResolver, corruptedStorageFileHandler),
                config,
                null,
                null,
                null,
                null,
                Config.DEFAULT_FULL_DAO_NODE,
                null,
                null,
                Config.UNSPECIFIED_PORT);
        ProvidersRepository providersRepository = new ProvidersRepository(config, new ArrayList<>(), true);
        this.priceFeedService = new PriceFeedService(new PriceHttpClient(null), providersRepository, preferences);

        this.p2PService = new P2PService(networkNode,
                peerManager,
                p2PDataStorage,
                requestDataManager,
                peerExchangeManager,
                keepAliveManager,
                broadcaster,
                socks5ProxyProvider,
                encryptionService,
                keyRing,
                mailboxMessageService);

        // Wiring up the statistics managers is complicated from here, but
        // the API test harness can make sure ApiStatisticsManager works
        // as expected.
    }

    public void initializeServices() {
        appendOnlyDataStoreService.readFromResources(storeFilenamePostFix, () -> {
        });
        PersistenceManager.onAllServicesInitialized();
        sleep(3_000); // Let service threads finish.  (TODO need a delay param?)
    }

    public PersistenceManager createPersistenceManager(File storageDir) {
        return new PersistenceManager<>(storageDir, corePersistenceProtoResolver, corruptedStorageFileHandler);
    }

    public void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            // ignored
        }
    }

    public void shutdown() {
        log.info("Shutting down " + p2PDataStorage.getClass().getSimpleName());
        // TODO Does anything else need to be explicitly shut down?
        p2PDataStorage.shutDown();
        log.info("Shut down");

        try {
            log.info("Deleting storage directory {}", storageDir.getAbsolutePath());
            FileUtil.deleteDirectory(storageDir);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private File createStorageDirs() {
        try {
            File rootDir = Files.createTempDirectory("bisq-test-storage-").toFile();

            File dbDir = new File(rootDir.getAbsolutePath(), "db");
            dbDir.mkdir();
            File keysDir = new File(rootDir.getAbsolutePath(), "keys");
            keysDir.mkdir();

            return rootDir;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
