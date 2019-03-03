#!/usr/bin/env bash
# This script will download and install the appropriate JDK for use with Bisq development.
# It will also configure it as the default system JDK.
# If you need to change to another default JDK for another purpose later, you can use the
# following commands and select the default JDK:
# Linux:
#     update-alternatives --config java
#     update-alternatives --config javac
# MacOS:
#     echo 'export JAVA_HOME=/Library/Java/JavaVirtualMachines/<ENTER_NEW_JDK>/Contents/Home' >>~/.bash_profile
#     echo 'export PATH=$JAVA_HOME/bin:$PATH' >>~/.bash_profile
#     source ~/.bash_profile


unameOut="$(uname -s)"
case "${unameOut}" in
    Linux*)     
        JAVA_HOME=/usr/lib/jvm/openjdk-10.0.2
        JDK_FILENAME=openjdk-10.0.2_linux-x64_bin.tar.gz
        JDK_URL=https://download.java.net/java/GA/jdk10/10.0.2/19aef61b38124481863b1413dce1855f/13/openjdk-10.0.2_linux-x64_bin.tar.gz

        # Determine which package manager to use depending on the distribution
        declare -A osInfo;
        osInfo[/etc/redhat-release]=yum
        osInfo[/etc/arch-release]=pacman
        osInfo[/etc/gentoo-release]=emerge
        osInfo[/etc/SuSE-release]=zypp
        osInfo[/etc/debian_version]=apt-get

        for f in ${!osInfo[@]}
        do
            if [[ -f $f ]]; then
                PACKAGE_MANAGER=${osInfo[$f]}
                break
            fi
        done

        if [ ! -d "$JAVA_HOME" ]; then
            # Ensure curl is installed since it may not be
            $PACKAGE_MANAGER -y install curl

            curl -L -O $JDK_URL
            mkdir -p $JAVA_HOME
            tar -zxf $JDK_FILENAME -C $JAVA_HOME --strip 1
            rm $JDK_FILENAME

            update-alternatives --install /usr/bin/java java $JAVA_HOME/bin/java 2000
            update-alternatives --install /usr/bin/javac javac $JAVA_HOME/bin/javac 2000
        fi

        update-alternatives --set java $JAVA_HOME/bin/java
        update-alternatives --set javac $JAVA_HOME/bin/javac
        ;;
    Darwin*)
        JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-10.0.2.jdk/Contents/Home
        JDK_FILENAME=openjdk-10.0.2_osx-x64_bin.tar.gz
        JDK_URL=https://download.java.net/java/GA/jdk10/10.0.2/19aef61b38124481863b1413dce1855f/13/openjdk-10.0.2_osx-x64_bin.tar.gz
        if [ ! -d "$JAVA_HOME" ]; then
            if [[ $(command -v brew) == "" ]]; then
                echo "Installing Hombrew"
                /usr/bin/ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"
            else
                echo "Updating Homebrew"
                brew update
            fi

            brew install curl
            curl -L -O $JDK_URL
            sudo mkdir /Library/Java/JavaVirtualMachines/openjdk-10.0.2.jdk | sudo bash
            gunzip -c $JDK_FILENAME | tar xopf -
            sudo mv jdk-10.0.2.jdk/* /Library/Java/JavaVirtualMachines/openjdk-10.0.2.jdk
            sudo rmdir jdk-10.0.2.jdk
            rm $JDK_FILENAME
        fi

        echo export JAVA_HOME=$JAVA_HOME >>~/.bash_profile
        echo export PATH=$JAVA_HOME/bin:$PATH >>~/.bash_profile
        source ~/.bash_profile
        ;;
    *)          machine="UNKNOWN:${unameOut}"
esac
java -version
