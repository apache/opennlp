#!/bin/sh
. opennlp-env
$JAVA_CMD opennlp.tools.postag.POSTaggerME $@
