#!/bin/bash
/etc/init.d/tomcat stop; sleep 4 && /bin/cp /tmp/phoenix.war /usr/share/apache-tomcat-7.0.34/webapps/ && /bin/rm -rf /usr/share/apache-tomcat-7.0.34/webapps/phoenix && /etc/init.d/tomcat start
