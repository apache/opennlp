#!/bin/sh

echo
echo "Maxent Build System"
echo "-------------------"
echo

if [ "$JAVA_HOME" = "" ] ; then
  echo "ERROR: JAVA_HOME not found in your environment."
  echo
  echo "Please, set the JAVA_HOME variable in your environment to match the"
  echo "location of the Java Virtual Machine you want to use."
  exit 1
fi

if [ `echo $OSTYPE | grep -n cygwin` ]; then
  PS=";"
else
  PS=":"
fi

LOCALCLASSPATH=$JAVA_HOME/lib/tools.jar
# add in the dependency .jar files
DIRLIBS=lib/*.jar
for i in ${DIRLIBS}
do
    if [ "$i" != "${DIRLIBS}" ] ; then
        LOCALCLASSPATH=$LOCALCLASSPATH${PS}"$i"
    fi
done
ANT_HOME=./lib

echo Building with classpath $LOCALCLASSPATH
echo

echo Starting Ant...
echo

# One person found a seg fault with jdk 1.3.0 on Linux where adding -classic
# to the following line fixed the issue

$JAVA_HOME/bin/java -Dant.home=$ANT_HOME -classpath $LOCALCLASSPATH${PS}$ADDITIONALCLASSPATH org.apache.tools.ant.Main $*
