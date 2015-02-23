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

ssh pmail 'sudo /bin/rm /tmp/phoenix.war'
scp $WAR pmail:/tmp/phoenix.war
ssh pmail 'sudo chown tomcat:tomcat /tmp/phoenix.war'

echo "Now perform on the server:"
echo "mv /usr/share/apache-tomcat-7.0.34/webapps/phoenix.war /home/phonex/phoenix.war.prev"
echo "cp /tmp/phoenix.war /usr/share/apache-tomcat-7.0.34/webapps/"
echo "/etc/init.d/tomcat stop"
echo "/etc/init.d/tomcat start"

echo "/etc/init.d/tomcat stop; sleep 2; mv /usr/share/apache-tomcat-7.0.34/webapps/phoenix.war /home/phonex/phoenix.war.prev; /bin/cp /tmp/phoenix.war /usr/share/apache-tomcat-7.0.34/webapps/; /bin/rm -rf /usr/share/apache-tomcat-7.0.34/webapps/phoenix; /etc/init.d/tomcat start"

