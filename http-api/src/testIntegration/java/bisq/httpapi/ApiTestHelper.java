package bisq.httpapi;

@SuppressWarnings("WeakerAccess")
public final class ApiTestHelper {

    public static void waitForAllServicesToBeReady() throws InterruptedException {
//        TODO it would be nice to expose endpoint that would respond with 200
        // PaymentMethod initializes it's static values after all services get initialized
        int ALL_SERVICES_INITIALIZED_DELAY = 5000;
        Thread.sleep(ALL_SERVICES_INITIALIZED_DELAY);
    }

}
