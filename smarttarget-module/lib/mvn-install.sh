#!/bin/bash
# Install SDL SmartTarget libraries and necessary third-party libraries in the local Maven repository

echo "Installing SDL SmartTarget libraries into the local Maven repository..."

mvn -q install:install-file -DgroupId=com.tridion -DartifactId=smarttarget_core -Dversion=2.1.0 -Dpackaging=jar -Dfile=smarttarget_core-2.1.0.jar
mvn -q install:install-file -DgroupId=com.tridion -DartifactId=smarttarget_entitymodel -Dversion=2.1.0 -Dpackaging=jar -Dfile=smarttarget_entitymodel-2.1.0.jar
mvn -q install:install-file -DgroupId=com.tridion -DartifactId=smarttarget_cartridge -Dversion=2.1.0 -Dpackaging=jar -Dfile=smarttarget_cartridge-2.1.0.jar
mvn -q install:install-file -DgroupId=com.tridion -DartifactId=smarttarget_api_webservice -Dversion=2.1.0 -Dpackaging=jar -Dfile=smarttarget_api_webservice-2.1.0.jar
mvn -q install:install-file -DgroupId=com.tridion -DartifactId=session_cartridge -Dversion=2.1.0 -Dpackaging=jar -Dfile=session_cartridge-2.1.0.jar