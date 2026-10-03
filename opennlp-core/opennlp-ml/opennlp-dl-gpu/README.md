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

# OpenNLP DL (GPU)

This module brings in `onnxruntime_gpu` bindings to the existing `opennlp-dl` module. If you are planning to run with GPU acceleration, please use this BOM.

You can use it in your code by adding the following as a dependency:

```xml
<dependency>
    <groupId>org.apache.opennlp</groupId>
    <artifactId>opennlp-dl-gpu</artifactId>
    <version>${opennlp.version}</version>
 </dependency>
```