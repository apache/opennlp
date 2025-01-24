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

[![GitHub license](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://raw.githubusercontent.com/apache/opennlp/main/LICENSE)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.opennlp/opennlp/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.apache.opennlp/opennlp)
[![Documentation Status](https://img.shields.io/:docs-latest-green.svg)](http://opennlp.apache.org/docs/index.html)
[![Build Status](https://github.com/apache/opennlp/workflows/Java%20CI/badge.svg)](https://github.com/apache/opennlp/actions)
[![Contributors](https://img.shields.io/github/contributors/apache/opennlp)](https://github.com/apache/opennlp/graphs/contributors)
[![GitHub pull requests](https://img.shields.io/github/issues-pr-raw/apache/opennlp.svg)](https://github.com/apache/opennlp/pulls)
[![OpenSSF Scorecard](https://api.securityscorecards.dev/projects/github.com/apache/opennlp/badge)](https://api.securityscorecards.dev/projects/github.com/apache/opennlp)

The Apache OpenNLP library is a machine learning based toolkit for the processing of natural language text.

This toolkit is written completely in Java and provides support for common NLP tasks, such as tokenization,
 sentence segmentation, part-of-speech tagging, named entity extraction, chunking, parsing,
  coreference resolution, language detection and more!

These tasks are usually required to build more advanced text processing services.

The goal of the OpenNLP project is to be a mature toolkit for the above mentioned tasks.

An additional goal is to provide a large number of pre-built models for a variety of languages, as
well as the annotated text resources that those models are derived from.

Presently, OpenNLP includes common classifiers such as Maximum Entropy, Perceptron and Naive Bayes.

OpenNLP can be used both programmatically through its Java API or from a terminal through its CLI. 
OpenNLP API can be easily plugged into distributed streaming data pipelines like Apache Flink, Apache NiFi, Apache Spark.

## Useful Links
       
For additional information, visit the [OpenNLP Home Page](http://opennlp.apache.org/)

You can use OpenNLP with any language, demo models are provided [here](https://downloads.apache.org/opennlp/models/).

The models are fully compatible with the latest release, they can be used for testing or getting started. 

> [!NOTE]  
> Please train your own models for all other use cases.

Documentation, including JavaDocs, code usage and command-line interface examples are available [here](http://opennlp.apache.org/docs/)

For recent news, updates and topics, you can:  
- join the regular [mailing lists](http://opennlp.apache.org/mailing-lists.html), 
- follow the project's [![Bluesky](https://img.shields.io/badge/Bluesky-0285FF?logo=bluesky&logoColor=fff)](https://bsky.app/profile/apacheopennlp.bsky.social) social media channel, or
- join the [![Slack](https://img.shields.io/badge/Slack-4A154B?logo=slack&logoColor=fff)](https://the-asf.slack.com) channel (available to people with an _@apache.org_ email address or upon invitation). 

Please, also check the [![Stack Overflow](https://img.shields.io/badge/-Stack%20Overflow-FE7A16?logo=stack-overflow&logoColor=white)](https://stackoverflow.com/questions/tagged/opennlp) community's OpenNLP questions and answers.

## Overview

Currently, the library has different packages:

* `opennlp-tools` : The core toolkit.
* `opennlp-tools-models` : A set of classes to load [OpenNLP models](https://github.com/apache/opennlp-models) from the classpath.
* `opennlp-uima` : A set of [Apache UIMA](https://uima.apache.org) annotators.
* `opennlp-morfologik-addon` : An addon for Morfologik
* `opennlp-dl` : OpenNLP interface implementations for ONNX models using the `onnxruntime` dependency.
* `opennlp-dl-gpu` : Replaces `onnxruntime` with the `onnxruntime_gpu` dependency to support GPU acceleration.
* `opennlp-sandbox`: Other projects in progress are found in the [sandbox](https://github.com/apache/opennlp-sandbox)

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
compile group: "org.apache.opennlp", name: "opennlp-tools", version: "${opennlp.version}"
```

For more details please check our [documentation](http://opennlp.apache.org/docs/)

## Building OpenNLP

At least JDK 17 and Maven 3.3.9 are required to build the library.

After cloning the repository go into the destination directory and run:

```
mvn install
```

### Additional Developement Information

- [Building and Integrating Snowball Stemmer for OpenNLP](dev/Snowball-Stemmer.md)

## Contributing

The Apache OpenNLP project is developed by volunteers and is always looking for new contributors to work on all parts of the project. Every contribution is welcome and needed to make it better. A contribution can be anything from a small documentation typo fix to a new component.

If you would like to get involved please follow the instructions [here](https://github.com/apache/opennlp/blob/main/.github/CONTRIBUTING.md)
