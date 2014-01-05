#!/bin/bash
#
# Publishes this war to the server.
#

# get current directory 
DIR="$( cd -P "$( dirname "$0" )" && pwd )"; 

# change current directory 
cd "$DIR" 

WAR="target/phoenix-soap-app-1.0.0-SNAPSHOT.war"
if [ ! -f $WAR ]; then
	echo "WAR does not exist [$WAR]"
	exit 2
fi

scp $WAR pmail:/tmp/phoenix.war
echo "Now perform on the server:"
echo "cp /tmp/phoenix.war /usr/share/apache-tomcat-7.0.34/webapps/"
echo "/etc/init.d/tomcat stop"
echo "/etc/init.d/tomcat start"


