# Importing Bisq into IntelliJ IDEA

Most Bisq contributors use IDEA for development. The following instructions have been tested on IDEA 2021.1.

1. Follow the instructions in [build.md](build.md) to clone and build Bisq at the command line.
1. Open IDEA
1. Go to `File -> Settings -> Build, Execution, Deployment -> Compiler -> Annotation Processors` and check the `Enable annotation processing` option to enable processing of Lombok annotations (Lombok plugin installed by default since v2020.3)
1. Go to `File -> New -> Project from Existing Sources...` and then select the main Bisq folder to load automatically the related Gradle project
1. If you did not yet setup JDK11 in IntelliJ, go to `File-> Project Structure -> Project` and under the `Project SDK` option locate your JDK11 folder
1. Select JDK 11 for Gradle as well. Go to `File -> Settings -> Build, Execution, Deployment -> Build Tools -> Gradle` and select the JDK11 location for the Gradle JVM value
1. Go to `Build -> Build Project`. Everything should build cleanly
1. Go to `Run > Edit Configurations... -> Plus (+) icon on the top left -> Application` anf then fill the requested fields as shown below, while using as CLI arguments one of those listed in [dev-setup.md](dev-setup.md):

![edit_configurations.png](edit_configurations.png)

9. Now you should be able to run Bisq by clicking on the _Play_ button or via `Run -> Run 'Bisq Desktop'`
10. If you want to debug the application and execute breakpoints, use `Run -> Debug 'Bisq Desktop'`

> TIP: If you encounter compilation errors in IDEA related to the `protobuf.*` classes, it is probably because you didn't build Bisq at the command line as instructed above. You need to run the `generateProto` task in the `other` project. You can do this via the Gradle tool window in IDEA, or you can do it the command line with `./gradlew :other:generateProto`. Once you've done that, run `Build -> Build Project` again and you should have no errors.
>
> If this does not solve the issue, try to execute `./gradlew clean` and then rebuild the project again.
