<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title><#if (content.title)??><#escape x as x?xml>${content.title} - Apache OpenNLP</#escape><#else>Apache OpenNLP</#if></title>
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="Apache OpenNLP  is a machine learning based toolkit for the processing of natural language text." />
    <meta name="author" content="The Apache OpenNLP Team" />
    <meta name="keywords" content="java, natural language processing, nlp, apache, open source, web site" />
    <meta name="generator" content="JBake"/>

    <link rel="alternate" type="application/rss+xml" title="RSS" href="/${config.feed_file}" />
    <link rel="shortcut icon" href="/favicon.ico" />

    <!-- Le styles -->
    <link href="/css/bootstrap.min.css" rel="stylesheet">
    <link href="/css/asciidoctor.css" rel="stylesheet">
    <link href="/css/prettify.css" rel="stylesheet">
    <style type="text/css">
/* Sticky footer styles
-------------------------------------------------- */
html {
  position: relative;
  min-height: 100%;
}
body {
  /* Margin bottom by footer height */
  margin-bottom: 80px;
}
.footer {
  position: absolute;
  bottom: 0;
  width: 100%;
  /* Set the fixed height of the footer here */
  height: 80px;
  background-color: #f5f5f5;
  overflow: hidden;
}

body > .container {
  padding: 60px 15px 0;
}
.footer .text-muted {
  margin: 20px 0;
}

.footer > .container {
  padding-right: 15px;
  padding-left: 15px;
}

.jumbotron {
  margin: 0px 0;
  text-align: center;
  background-color: transparent;
  padding-top: 0px;
}

.jumbotron h1 {
    line-height: 1;
    font-weight: bold;
}

    </style>

    <!-- HTML5 shim, for IE6-8 support of HTML5 elements -->
    <!--[if lt IE 9]>
      <script src="/js/html5shiv.js"></script>
    <![endif]-->

    <!-- Fav and touch icons -->
    <!--<link rel="apple-touch-icon-precomposed" sizes="144x144" href="../assets/ico/apple-touch-icon-144-precomposed.png">
    <link rel="apple-touch-icon-precomposed" sizes="114x114" href="../assets/ico/apple-touch-icon-114-precomposed.png">
    <link rel="apple-touch-icon-precomposed" sizes="72x72" href="../assets/ico/apple-touch-icon-72-precomposed.png">
    <link rel="apple-touch-icon-precomposed" href="../assets/ico/apple-touch-icon-57-precomposed.png">
    <link rel="shortcut icon" href="../assets/ico/favicon.png">-->
</head>
<body onload="prettyPrint()">
   