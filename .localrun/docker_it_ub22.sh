#! /bin/bash

_HOME2_=$(dirname $0)
export _HOME2_
_HOME_=$(cd $_HOME2_;pwd)
export _HOME_

echo $_HOME_
cd $_HOME_


if [ "$1""x" == "buildx" ]; then
    cp -a ../buildscripts .
    docker build -f Dockerfile_ub22 -t mqmon_java_001_ub22 .
    exit 0
fi

build_for='
ubuntu:22.04
'

for system_to_build_for in $build_for ; do

    system_to_build_for_orig="$system_to_build_for"
    system_to_build_for=$(echo "$system_to_build_for_orig" 2>/dev/null|tr ':' '_' 2>/dev/null)

    cd $_HOME_/
    mkdir -p $_HOME_/"$system_to_build_for"/

    mkdir -p $_HOME_/"$system_to_build_for"/artefacts
    mkdir -p $_HOME_/"$system_to_build_for"/script
    mkdir -p $_HOME_/"$system_to_build_for"/workspace

    ls -al $_HOME_/"$system_to_build_for"/

    rsync -a ../ --exclude=.localrun $_HOME_/"$system_to_build_for"/workspace/build
    chmod a+rwx -R $_HOME_/"$system_to_build_for"/workspace/build

    echo '#! /bin/bash

javac -version

pwd
cd /workspace/build/

cp ./setup.txt /tmp/setup.txt
chmod a+r /tmp/setup.txt

id -a

mkdir _mq/
cd _mq/
# wget https://raw.githubusercontent.com/ibm-messaging/mq-dev-samples/master/gettingStarted/installing-mq-ubuntu/mq-ubuntu-install.sh -O mq-ubuntu-install.sh
# chmod 755 mq-ubuntu-install.sh
# ./mq-ubuntu-install.sh

cd /tmp/
tar -xzvf mqadv_dev920_ubuntu_x86-64.tar.gz
cd MQServer/
./mqlicense.sh -accept

dpkg -i ibmmq-runtime_9.2.0.0_amd64.deb
dpkg -i ibmmq-gskit_9.2.0.0_amd64.deb
dpkg -i ibmmq-server_9.2.0.0_amd64.deb

dpkg -i ibmmq-client_9.2.0.0_amd64.deb
dpkg -i ibmmq-java_9.2.0.0_amd64.deb
dpkg -i ibmmq-jre_9.2.0.0_amd64.deb
dpkg -i ibmmq-amqp_9.2.0.0_amd64.deb

dpkg -l|grep -i mq

cat /tmp/mqconfig*.log

/opt/mqm/bin/dspmqver

tmpf="/tmp/cmd.$$.txt"
rm -f "$tmpf"
echo "cd /opt/mqm/bin;. setmqenv -s;crtmqm QM1;strmqm QM1;cat /tmp/setup.txt | runmqsc QM1;setmqaut -m QM1 -t qmgr -g mqclient +connect +inq;setmqaut -m QM1 -n DEV.** -t queue -g mqclient +put +get +browse +inq" > "$tmpf"

chmod a+r "$tmpf"
cat "$tmpf"
sudo -u mqm /bin/bash "$tmpf"
rm -f "$tmpf"
echo "-------- installation done --------"

cd /workspace/build/
rm mqmon.class
javac -cp ./com.ibm.mq.allclient-9.2.4.0.jar mqmon.java
ls -al mqmon.class || exit 1

echo "=========11111=========="
java -cp ./com.ibm.mq.allclient-9.2.4.0.jar:./json-20211205.jar:./ mqmon localhost 1414 QM1 DEV.APP.SVRCONN
echo "=========22222=========="
java -cp ./com.ibm.mq.allclient-9.2.4.0.jar:./json-20211205.jar:./ mqmon -c0 localhost 1414 QM1 DEV.APP.SVRCONN
echo "=========33333=========="
java -cp ./com.ibm.mq.allclient-9.2.4.0.jar:./json-20211205.jar:./ mqmon -c6 localhost 1414 QM1 DEV.APP.SVRCONN
echo "=========44444=========="
java -cp ./com.ibm.mq.allclient-9.2.4.0.jar:./json-20211205.jar:./ mqmon -c99 localhost 1414 QM1 DEV.APP.SVRCONN
echo "=========EEEEE=========="

# chmod a+rwx /artefacts/*

' > $_HOME_/"$system_to_build_for"/script/run.sh

    docker run -ti --rm \
      -v $_HOME_/"$system_to_build_for"/artefacts:/artefacts \
      -v $_HOME_/"$system_to_build_for"/script:/script \
      -v $_HOME_/"$system_to_build_for"/workspace:/workspace \
      --net=host \
     "mqmon_java_001_ub22" \
     /bin/sh -c "apk add bash >/dev/null 2>/dev/null; /bin/bash /script/run.sh"
     if [ $? -ne 0 ]; then
        echo "** ERROR **:$system_to_build_for_orig"
        exit 1
     else
        echo "--SUCCESS--:$system_to_build_for_orig"
     fi

done


