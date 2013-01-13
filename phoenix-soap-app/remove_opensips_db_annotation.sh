#!/bin/bash
# removes catalog="phoenixdb" annotation from opensips entities

# get current directory 
DIR="$( cd -P "$( dirname "$0" )" && pwd )"; 

# change current directory 
cd "$DIR" 

find src/main/java/com/phoenix/db/opensips/ -type f -name '*.java' -exec sed -i 's/,\s*catalog\s*=\s*"phoenixdb"//g' {} \;

