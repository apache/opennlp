<#--
   Licensed to the Apache Software Foundation (ASF) under one
   or more contributor license agreements.  See the NOTICE file
   distributed with this work for additional information
   regarding copyright ownership.  The ASF licenses this file
   to you under the Apache License, Version 2.0 (the
   "License"); you may not use this file except in compliance
   with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing,
   software distributed under the License is distributed on an
   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   KIND, either express or implied.  See the License for the
   specific language governing permissions and limitations
   under the License. 
-->
<#include "header.ftl">

<#include "menu.ftl">

<div class="container">
    <!-- Jumbotron -->
    <div class="jumbotron" style="text-align: center">
        <img src="/img/opennlp-logo.png" alt="Apache OpenNLP logo" />
        <h1>Welcome to Apache OpenNLP</h1>
        <p class="lead">The Apache OpenNLP library is a machine learning based toolkit for the processing of natural language text.</p>
        <a class="btn btn-lg btn-primary" href="/docs/" role="button" style="background-color: #832778; border: 1px solid #832778;"><span class="glyphicon glyphicon-chevron-right"></span> Get started</a>&nbsp;
        <a class="btn btn-lg btn-primary" href="/download.html" role="button" style="background-color: #BE2043; border: 1px solid #BE2043;"><span class="glyphicon glyphicon-download-alt"></span> Download</a>
    </div>

    <div class="row">
        <div class="col-lg-4">
            <h2>About</h2>
            <p>OpenNLP supports the most common NLP tasks, such as <i>tokenization</i>, <i>sentence segmentation</i>, <i>part-of-speech tagging</i>, <i>named entity extraction</i>, <i>chunking</i>, <i>parsing</i>, and <i>coreference resolution</i>.</p>
            <p>Find out more about it in our <a href="/docs/">manual</a>.</p>
        </div>
        <div class="col-lg-4">
            <h2>Getting Involved</h2>
            <p>The Apache OpenNLP project is developed by volunteers and is always looking for new contributors to work on all parts of the project. Every contribution is welcome and needed to make it better. A contribution can be anything from a small documentation typo fix to a new component.</p>
            <p>Learn more about how you can <a href="/get-involved.html">get involved</a>.</p>
        </div>
        <div class="col-lg-4" style="height: 300px; overflow-y: scroll;">
            <p><a class="twitter-timeline" href="https://twitter.com/ApacheOpennlp">Tweets by ApacheOpennlp</a> <script async src="//platform.twitter.com/widgets.js" charset="utf-8"></script></p>
        </div>
    </div>

</div>

<#include "footer.ftl">