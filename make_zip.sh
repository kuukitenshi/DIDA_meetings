#!/usr/bin/env bash

mvn clean eclipse:clean
zip -r dida-meetings-group11.zip app/ configs/ console/ contract/ core/ server/ util/ README.md pom.xml setup_env.sh
