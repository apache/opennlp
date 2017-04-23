<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

Welcome to Apache OpenNLP!
===========

[![Build Status](https://api.travis-ci.org/apache/opennlp.svg?branch=master)](https://travis-ci.org/apache/opennlp)
[![Coverage Status](https://coveralls.io/repos/github/apache/opennlp/badge.svg?branch=master)](https://coveralls.io/github/apache/opennlp?branch=master)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.opennlp/opennlp/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/org.apache.opennlp/opennlp)
[![Documentation Status](https://img.shields.io/:docs-latest-green.svg)](http://opennlp.apache.org/documentation.html)
[![GitHub license](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://raw.githubusercontent.com/apache/opennlp/master/LICENSE)
[![Twitter Follow](https://img.shields.io/twitter/follow/ApacheOpennlp.svg?style=social)](https://twitter.com/ApacheOpenNLP)

The Apache OpenNLP library is a machine learning based toolkit for the processing of natural language text.
It supports the most common NLP tasks, such as tokenization, sentence segmentation,
part-of-speech tagging, named entity extraction, chunking, parsing, and coreference resolution.
These tasks are usually required to build more advanced text processing services.
OpenNLP also includes maximum entropy and perceptron based machine learning.
      
The goal of the OpenNLP project is to create a mature toolkit for the above mentioned tasks.
An additional goal is to provide a large number of pre-built models for a variety of languages, as
well as the annotated text resources that those models are derived from.

For additional information about OpenNLP, visit the [OpenNLP Home Page](http://opennlp.apache.org/)

Documentation for OpenNLP, including JavaDocs, code usage and command line interface are available [here](http://opennlp.apache.org/documentation.html)

#### Using OpenNLP as a Library

Running any application that uses OpenNLP will require installing a binary or source version and setting the environment.
To compile from source:

* `mvn -DskipTests clean install`
* To run tests do `mvn test`

To use maven, add the appropriate setting to your pom.xml or build.sbt following the template below.

```
<dependency>
    <groupId>org.apache.opennlp</groupId>
    <artifactId>opennlp-tools</artifactId>
    <version>${opennlp.version}</version>
</dependency>
```

