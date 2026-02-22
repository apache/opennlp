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
[![Maven Central](https://img.shields.io/maven-central/v/org.apache.opennlp/opennlp)](https://img.shields.io/maven-central/v/org.apache.opennlp/opennlp)
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

Currently, the library has different modules:

* `opennlp-api` : The public API defining core Apache OpenNLP interfaces and abstractions.
* `opennlp-runtime` : The core classes shared across Apache OpenNLP components.
* `opennlp-ml-commons` : Common utilities and shared functionality for ML implementations.
* `opennlp-ml-maxent` : Maximum Entropy (MaxEnt) machine learning implementation.
* `opennlp-ml-bayes` : Naive Bayes machine learning implementation.
* `opennlp-ml-perceptron` : Perceptron-based machine learning implementation.
* `opennlp-dl` : Apache OpenNLP adapter for [ONNX](https://onnx.ai) models using the `onnxruntime` dependency.
* `opennlp-dl-gpu` : Replaces `onnxruntime` with the `onnxruntime_gpu` dependency to support GPU acceleration.
* `opennlp-models` : Classes for working with Apache OpenNLP model artifacts.
* `opennlp-formats` : Support for reading and writing various NLP training and data formats.
* `opennlp-cli` : The command-line tools for training, evaluating, and running models.
* `opennlp-tools` : The full end-user toolkit with all core components and utilities in its executable form.
* `opennlp-morfologik` : Extension module providing Morfologik-based dictionary and stemming support.
* `opennlp-uima` : Extension module providing a set of [Apache UIMA](https://uima.apache.org) annotators.
* `opennlp-sandbox` : Other projects in progress reside in the [sandbox](https://github.com/apache/opennlp-sandbox).
      

## Getting Started

You can import the core toolkit directly from Maven or Gradle:

#### Maven

```
<dependency>
    <groupId>org.apache.opennlp</groupId>
    <artifactId>opennlp-runtime</artifactId>
    <version>${opennlp.version}</version>
</dependency>
<!-- if model support is needed -->
<dependency>
    <groupId>org.apache.opennlp</groupId>
    <artifactId>opennlp-models</artifactId>
    <version>${opennlp.version}</version>
</dependency>
```

Note: `opennlp-runtime` ships with the MaxEnt ML implementation by default. If you need other ML implementations, please add the corresponding dependencies as well.

#### Gradle

```
compile group: "org.apache.opennlp", name: "opennlp-runtime", version: "${opennlp.version}"
compile group: "org.apache.opennlp", name: "opennlp-models", version: "${opennlp.version}"
```

For more details please check our [documentation](https://opennlp.apache.org/docs/)

## Migrating from 2.x to 3.x

The 3.x release line of Apache OpenNLP introduces **no** known breaking changes but modularizes the project for better usage as a library and to support future extensibility.
The core API remains stable and compatible with 2.x, but the project structure has been reorganized into multiple modules.

That means, that you can continue to use the previous `opennlp-tools` artifact as a dependency. However, we strongly recommend to switch to the new modular structure 
and import only the components you need, which will result in a smaller dependency footprint.

Only `opennlp-runtime` needs to be added as a dependency, and you can add additional modules (e.g. `opennlp-ml-maxent`, `opennlp-models`, etc.) as required by your project.
For users of the traditional CLI toolkit, nothing changes with the 3.x release line. CLI usage remains stable as described in the [project's dev manual](https://opennlp.apache.org/docs/).

### Head's up

The Apache OpenNLP team is planning to change the package namespace from `opennlp` to `org.apache.opennlp` in a future release (potentially 4.x). 
This change will be made to align with standard Java package naming conventions and to avoid potential conflicts with other libraries.

In addition, the Apache OpenNLP team is considering the raise of the minimal Java version to JDK 21+ in a future release (potentially 4.x) 
to take advantage of the latest language features and improvements.

## Branches and Merging Strategy

To support ongoing development and stable maintenance of Apache OpenNLP, the project follows a dual-branch model:

### Branch overview

- **`main`**: Development branch for version **3.0** and beyond. All feature development and 3.x releases occur here.
- **`opennlp-2.x`**: Maintains the stable **2.x** release line. This branch will receive selective updates and patch releases.

### Workflow summary

- Feature development
  - New features targeting versions 3.0+ are developed on feature branches _off_ `main` and merged _into_ `main`.
- Bug fixes and dependency updates
  - Relevant fixes or dependency updates from `main` may be cherry-picked into `opennlp-2.x` as needed.
- Releases
  - **3.x** releases are made from the `main` branch.
  - **2.x** releases are made from the `opennlp-2.x` branch.
- Release tags
  - Release tags are applied directly to the appropriate version branch (`main` for 3.x or `opennlp-2.x` for 2.x). 
  - The presence of a version branch does not affect the tagging or visibility of releases.

## Building OpenNLP

At least JDK 17 and Maven 3.3.9 are required to build the library.

After cloning the repository go into the destination directory and run:

```
mvn install
```

### Additional Development Information

- Building and integrating [Snowball Stemmer](dev/Snowball-Stemmer.md) for OpenNLP.

## Contributing

The Apache OpenNLP project is developed by volunteers and is always looking for new contributors to work on all parts of the project. 
Every contribution is welcome and needed to make it better. 
A contribution can be anything from a small documentation typo fix to a new component.

If you would like to get involved please follow the instructions [here](https://github.com/apache/opennlp/blob/main/.github/CONTRIBUTING.md)
