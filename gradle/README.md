# How to upgrade the Gradle version

Visit the [Gradle website](https://gradle.org/releases/) and decide the:

 - desired version
 - desired distribution type
 - what is the sha256 for the version and type chosen above

Adjust the following command with tha arguments above and execute it twice:

    ./gradlew wrapper --gradle-version 6.6.1 \
        --distribution-type all \
        --gradle-distribution-sha256-sum 11657af6356b7587bfb37287b5992e94a9686d5c8a0a1b60b87b9928a2decde5

The first execution should automatically update:

 - `bisq/gradle/wrapper/gradle-wrapper.properties`

The second execution should then update:

 - `bisq/gradle/wrapper/gradle-wrapper.jar`
 - `bisq/gradlew`
 - `bisq/gradlew.bat`

The four updated files are ready to be committed.
