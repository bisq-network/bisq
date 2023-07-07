# How to upgrade the Gradle version

Visit the [Gradle website](https://gradle.org/releases/) and decide the:

 - desired version
 - desired distribution type
 - what is the sha256 for the version and type chosen above

Adjust the following command with tha arguments above and execute it twice:

    ./gradlew wrapper --gradle-version 8.2 \
        --distribution-type all \
        --gradle-distribution-sha256-sum 5022b0b25fe182b0e50867e77f484501dba44feeea88f5c1f13b6b4660463640

The first execution should automatically update:

- `bisq/gradle/wrapper/gradle-wrapper.properties`

The second execution should then update:

- `bisq/gradle/wrapper/gradle-wrapper.jar`
- `bisq/gradlew`
- `bisq/gradlew.bat`

The four updated files are ready to be committed.

To update the verification-metadata file run:

- `./gradlew --write-verification-metadata sha256`
