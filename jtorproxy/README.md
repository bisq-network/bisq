JTorProxy
=========
JTorProxy aims at providing an easy-to-use API for interfacing with Tor from Java. JTorProxy also supports hidden services through standard Java ServerSockets.

This is a fork of [Thali Project](http://www.thaliproject.org/)'s [Tor_Onion_Proxy_Library](https://github.com/thaliproject/Tor_Onion_Proxy_Library).<br>
Includes fixes, an easy-to-use high-level abstraction and an additional module, *net*, showcasing the code by providing an even higher-level API for bi-directional communication with self-advertising communication parties (see `io.nucleo.net.Node`).
Although barely tested, this layer is designed to also work with plain TCP Sockets for easier testability.

A simple test program is present in the sources of the *java* module, illustrating the use of the high-level API.
The net module, providing bi-directional communication with self-advertising communication parties, is utilised in a simple test program, `io.nucleo.net.node.NodeTest`, inside the *net* module.

Original readme follows:

README
======

NOTE: This project exists independently of the Tor Project.

__What__: Enable Android and Java applications to easily host their own Tor Onion Proxies using the core Tor binaries. Just by including an AAR or JAR an app can launch and manage the Tor OP as well as start a hidden service.

__Why__: It's sort of a pain to deploy and manage the Tor OP, we want to make it much easier.

__How__: We are really just a thin Java wrapper around the Tor OP binaries and jtorctl.

__Who__: This work is part of the [Thali Project](http://www.thaliproject.org/) and is being actively developed by Yaron Y. Goland assigned to the Microsoft Open Technologies Hub. We absolutely need your help! Please see the FAQ below if you would like to help!

# How do I use this library?
For now you get to build this yourself. Eventually, when it has enough testing, I might consider publishing it to some maven repository.

1. Install local maven (seriously, if you don't do this, nothing else will work)
2. Clone this repo
3. Install the JDK and if you are using Android, the Android SDK as well
4. Navigate to the 'universal' sub-directory and run 'gradlew install'

## Android
If you are going to use this library with Android then go to the 'android' sub-directory and run 'gradlew install'.

Now everything should be built and installed into your local maven.

In your Android project add the following to dependencies in build.gradle:
```groovy
    compile 'com.msopentech.thali:ThaliOnionProxyAndroid:0.0.2'
    compile 'org.slf4j:slf4j-android:1.7.7'
```

Also add mavenLocal() to your repositories in build.gradle (remember, this is the root level repositories, NOT repositories under buildscript).

While this code doesn't do much, using it is kind of a pain because of all the ceremony in Java land. So if you are going to host a Tor hidden service on your device or if you want to open connections to the Internet via Tor then there are a variety of things you need to know. My recommendation is that you open android/src/androidTest/java/com/msopentech/thali/toronionproxy/TorOnionProxySmokeTest.java and look up the method testHiddenServiceRecycleTime and start reading.

But everyone wants sample code so here is some sample code that will start a hidden service and open a socket to it.

```Java
String fileStorageLocation = "torfiles";
OnionProxyManager onionProxyManager =
        new AndroidOnionProxyManager(this.getApplicationContext(), fileStorageLocation);
int totalSecondsPerTorStartup = 4 * 60;
int totalTriesPerTorStartup = 5;

// Start the Tor Onion Proxy
if (onionProxyManager.startWithRepeat(totalSecondsPerTorStartup, totalTriesPerTorStartup) == false) {
    Log.e("TorTest", "Couldn't start Tor!");
    return;
}

// Start a hidden service listener
int hiddenServicePort = 80;
int localPort = 9343;
String onionAddress = onionProxyManager.publishHiddenService(hiddenServicePort, localPort);

// It can taken anywhere from 30 seconds to a few minutes for Tor to start properly routing
// requests to to a hidden service. So you generally want to try to test connect to it a
// few times. But after the previous call the Tor Onion Proxy will route any requests
// to the returned onionAddress and hiddenServicePort to 127.0.0.1:localPort. So, for example,
// you could just pass localPort into the NanoHTTPD constructor and have a HTTP server listening
// to that port.

// Connect via the TOR network
// In this case we are trying to connect to the hidden service but any IP/DNS address and port can be
// used here.
Socket clientSocket =
        Utilities.socks4aSocketConnection(onionAddress, hiddenServicePort, "127.0.0.1", localPort);

// Now the socket is open but note that it can take some time before the Tor network has everything
// connected and connection requests can fail for spurious reasons (especially when connecting to
// hidden services) so have lots of retry logic.
```

## Java
If you are going to use this library with Java then go to the 'java' sub-directory and run 'gradlew install'.

I would also recommend running 'gradlew test'. This will make sure you are properly set up and will copy your test files which I recommend reading.

Now everything should be build and installed into your local maven.

Now go to your build.gradle file (or equivalent) and make sure you add:
```groovy
apply plugin: 'maven'
```

Then go to your repositories and add:
```groovy
mavenLocal()
```

Then go to dependencies and add in:
```groovy
compile 'com.msopentech.thali:ThaliOnionProxyJava:0.0.2'
compile 'com.msopentech.thali:BriarJtorctl:0.0.2'
compile 'org.slf4j:slf4j-simple:1.7.7'
```

As discussed above, the code in this library is pretty trivial. But using it is hard because of the complexities of Tor and Java. For those in Java land please go to java\src\test\java\com\msopentech\thali\toronionproxy\TorOnionProxySmokeTest and check out testHiddenServiceRecycleTime().

But here is some sample code to get you started.

```Java
String fileStorageLocation = "torfiles";
OnionProxyManager onionProxyManager = new JavaOnionProxyManager(
        new JavaOnionProxyContext(
                Files.createTempDirectory(fileStorageLocation).toFile()));

int totalSecondsPerTorStartup = 4 * 60;
int totalTriesPerTorStartup = 5;

// Start the Tor Onion Proxy
if (onionProxyManager.startWithRepeat(totalSecondsPerTorStartup, totalTriesPerTorStartup) == false) {
    return;
}

// Start a hidden service listener
int hiddenServicePort = 80;
int localPort = 9343;
String onionAddress = onionProxyManager.publishHiddenService(hiddenServicePort, localPort);

// It can taken anywhere from 30 seconds to a few minutes for Tor to start properly routing
// requests to to a hidden service. So you generally want to try to test connect to it a
// few times. But after the previous call the Tor Onion Proxy will route any requests
// to the returned onionAddress and hiddenServicePort to 127.0.0.1:localPort. So, for example,
// you could just pass localPort into the NanoHTTPD constructor and have a HTTP server listening
// to that port.

// Connect via the TOR network
// In this case we are trying to connect to the hidden service but any IP/DNS address and port can be
// used here.
Socket clientSocket =
        Utilities.socks4aSocketConnection(onionAddress, hiddenServicePort, "127.0.0.1", localPort);

// Now the socket is open but note that it can take some time before the Tor network has everything
// connected and connection requests can fail for spurious reasons (especially when connecting to
// hidden services) so have lots of retry logic.
```

# Acknowledgements
A huge thanks to Michael Rogers and the Briar project. This project started by literally copying their code (yes, I asked first) which handled things in Android and then expanding it to deal with Java. We are also using Briar's fork of JTorCtl until their patches are accepted by the Guardian Project.

Another huge thanks to the Guardian folks for both writing JTorCtl and doing the voodoo to get the Tor OP running on Android.

And of course an endless amount of gratitude to the heroes of the Tor project for making this all possible in the first place and for their binaries which we are using for all our supported Java platforms.

# FAQ
## What's the relationship between universal, Java and android projects?
The universal project produces a JAR that contains code that is common to both the Java and Android versions of the project. We need this JAR available separately because we use this code to build other projects that also share code between Java and Android. So universal is very useful because we can include universal into our project's 'common' code project without getting into any Java or Android specific details.

On top of universal are the java and android projects. They contain code specific to those platforms along with collateral like binaries.

Note however that shared files like the jtorctl-briar, geoip and torrc are kept in Universal and we use a gradle task to copy them into the android and java projects.

One further complication are tests. Hard experience has taught that putting tests into universal doesn't work well because it means we have to write custom wrappers for each test in android and java in order to run them. So instead the tests live primarily in the android project and we use a gradle task to copy them over to the Java project. This lets us share identical tests but it means that all edits to tests have to happen in the android project. Any changes made to shared test code in the java project will be lost. This should not be an issue for anyone but a dev actually working on Tor_Onion_Proxy_Library, to users its irrelevant.

## What is the maturity of the code in this project?
Well the release version is currently 0.0.2 so that should say something. This is an alpha. We have (literally) one test. Obviously we need a heck of a lot more coverage. But we have run that test and it does actually work which means that the Tor OP is being run and is available.

## Can I run multiple programs next to each other that use this library?
Yes, they won't interfere with each other. We use dynamic ports for both the control and socks channel.

## Can I help with the project?
ABSOLUTELY! You will need to sign a [Contributor License Agreement](https://cla.msopentech.com/) before submitting your pull request. To complete the Contributor License Agreement (CLA), you will need to submit a request via the form and then electronically sign the Contributor License Agreement when you receive the email containing the link to the document. This needs to only be done once for any Microsoft Open Technologies OSS project.

Please make sure to configure git with a username and email address to use for your commits. Your username should be your GitHub username, so that people will be able to relate your commits to you. From a command prompt, run the following commands:
```
git config user.name YourGitHubUserName
git config user.email YourAlias@YourDomain
```

What we most need help with right now is test coverage. But we also have a bunch of features we would like to add. See our issues for a list.

## Where does jtorctl-briar.jar come from?
This is a fork of jtorctl with some fixes from Briar. So we got it out of Briar's depot. The plan is that jtorctl is supposed to accept Briar's changes and then we will start to build jtorctl ourselves from the Tor depot directly.

## Where did the binaries for the Tor OP come from?
### Android
The ARM binary for Android came from the OrBot distribution available at https://guardianproject.info/releases/. I take the latest PIE release qualify APK, unzip it and go to res/raw and then decompress tor.mp3 and go into bin and copy out the tor executable file and put it into android/src/main/assets

### Windows
I download the Expert Bundle for Windows from https://www.torproject.org/download/download.html.en and took tor.exe, libeay32.dll, libevent-2-0-5.dll, libgcc_s_sjlj-1.dll and ssleay32.dll from the Tor directory. I then need to zip them all together into a file called tor.zip and stick them into java/src/main/resources/native/windows/x86.

### Linux
I download the 32 bit Tor Browser Bundle for Linux from https://www.torproject.org/download/download.html.en and then unzipped and untared it and navigated to tor-browser_en-US\Browser\TorBrowser\Tor\ and copied out the tor and libevent-2.0.so.5 files. Note that for stupid reasons I really should fix I currently need to zip these files together into a file called tor.zip before sticking them into java/src/main/resources/native/linux/x86.

I then do the same thing but this time with the 64 bit download and put the results into the x64 linux sub-directory.

### OS/X
I download the OS/X Tor Browser bundle from https://www.torproject.org/download/download.html.en and using 7Zip opened my way into the dmg file inside of 0.unknown partition\TorBrowser.app\TorBrowser\Tor and copied out tor.real and libevent-2.0.5.dylib. And, as with Linux, I then need to zip those two files together into a file called tor.zip and put that into java/src/main/resources/native/osx/x64.

## Where did the geoip and geoip6 files come from?
I took them from the Data/Tor directory of the Windows Expert Bundle (see previous question).

## Why does the Android code require minSdkVersion 16?!?!?! Why so high?
The issue is the tor executable that I get from Guardian. To run on Lollipop the executable has to be PIE. But PIE support only started with SDK 16. So if I'm going to ship a PIE executable I have to set minSdkVersion to 16. But!!!!! Guardian actually also builds a non-PIE version of the executable. So if you have a use case that requires support for an SDK less than 16 PLEASE PLEASE PLEASE send mail to the [Thali Mailing list](https://pairlist10.pair.net/mailman/listinfo/thali-talk). We absolutely can fix this. We just haven't had a good reason to. So please give us one!
