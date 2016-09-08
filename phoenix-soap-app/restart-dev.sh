#!/bin/bash
/etc/init.d/tomcat-dev stop; sleep 4 && /bin/cp /tmp/phoenix.war /usr/share/apache-tomcat-7.0.34-dev/webapps/ && /bin/rm -rf /usr/share/apache-tomcat-7.0.34-dev/webapps/phoenix && /etc/init.d/tomcat-dev start
