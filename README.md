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

This toolkit is written completly in Java and provides support for common NLP tasks, such as tokenization, sentence segmentation, part-of-speech tagging, named entity extraction, chunking, parsing, coreference resolution and more!

These tasks are usually required to build more advanced text processing services.

The goal of the OpenNLP project is to be a mature toolkit for the abovementioned tasks.

An additional goal is to provide a large number of pre-built models for a variety of languages, as
well as the annotated text resources that those models are derived from.

Currently, OpenNLP also includes common classifiers such as Maximum Entropy, Perceptron and Naive Bayes.

OpenNLP can be used both programmatically through its Java API or from a terminal through its CLI.

## Useful Links
       
For additional information, visit the [OpenNLP Home Page](http://opennlp.apache.org/)

You can use OpenNLP with any language, demo models are provided [here](http://opennlp.sourceforge.net/models-1.5/).

The models are fully compatible with the latest release, they can be used for testing or getting started. 

Please train your own models for all other use cases.

Documentation, including JavaDocs, code usage and command-line interface examples are available [here](http://opennlp.apache.org/docs/)

You can also follow our [mailing lists](http://opennlp.apache.org/mailing-lists.html) for news and updates.

## Overview

Currently the library has different packages:

`opennlp-tools` : The core toolkit.

`opennlp-uima` : A set of [Apache UIMA](https://uima.apache.org) annotators.

`opennlp-brat-annotator` : A set of annotators for [BRAT](http://brat.nlplab.org/)

`opennlp-morfologik-addon` : An addon for Morfologik

`opennlp-sandbox`: Other projects in progress are found in the [sandbox](https://github.com/apache/opennlp-sandbox)


## Getting Started

You can import the core toolkit directly from Maven, SBT or Gradle:

#### Maven
```
<dependency>
    <groupId>org.apache.opennlp</groupId>
    <artifactId>opennlp-tools</artifactId>
    <version>${opennlp.version}</version>
</dependency>
```

#### SBT
```
libraryDependencies += "org.apache.opennlp" % "opennlp-tools" % "${opennlp.version}"
```

#### Gradle
```
compile group: "org.apache.opennlp", name: "opennlp-tools", version: "$opennlp.version"
```


For more details please check our [documentation](http://opennlp.apache.org/docs/)

## Building OpenNLP

At least JDK 8 and Maven 3.3.9 are required to build the library.

After cloning the repository go into the destination directory and run:

```
mvn install
```

## Contributing

The Apache OpenNLP project is developed by volunteers and is always looking for new contributors to work on all parts of the project. Every contribution is welcome and needed to make it better. A contribution can be anything from a small documentation typo fix to a new component.

If you would like to get involved please follow the instructions [here](https://github.com/apache/opennlp/blob/master/.github/CONTRIBUTING.md)