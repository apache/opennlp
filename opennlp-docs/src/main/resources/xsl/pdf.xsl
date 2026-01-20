<?xml version="1.0" encoding="utf-8"?>

<!--
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

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:import href="urn:docbkx:stylesheet"/>
  <xsl:import href="urn:docbkx:stylesheet/highlight.xsl"/>

  <!-- Defining general document properties -->
  <xsl:param name="paper.type">A4</xsl:param>

  <!-- Defining global options -->
  <xsl:param name="fop1.extensions" select="1"/>
  <xsl:param name="tablecolumns.extension" select="1"/>
  <xsl:param name="default.table.width">100%</xsl:param>
  <xsl:param name="body.start.indent">0.5cm</xsl:param>

  <xsl:attribute-set name="component.title.properties">
    <xsl:attribute name="start-indent">0cm</xsl:attribute>
  </xsl:attribute-set>

  <!-- Customizing indentation of section and sub-section titles -->
  <xsl:attribute-set name="section.properties">
    <xsl:attribute name="start-indent">0.0cm</xsl:attribute>
  </xsl:attribute-set>
  <xsl:attribute-set name="section.title.properties">
    <xsl:attribute name="start-indent">0.0cm</xsl:attribute>
  </xsl:attribute-set>
  <xsl:attribute-set name="section.level1.properties">
    <xsl:attribute name="start-indent">0.0cm</xsl:attribute>
  </xsl:attribute-set>
  <xsl:attribute-set name="section.title.level1.properties">
    <xsl:attribute name="start-indent">0cm</xsl:attribute>
    <xsl:attribute name="border-top">0.5pt solid black</xsl:attribute>
    <xsl:attribute name="border-bottom">0.5pt solid black</xsl:attribute>
    <xsl:attribute name="padding-top">6pt</xsl:attribute>
    <xsl:attribute name="padding-bottom">6pt</xsl:attribute>
  </xsl:attribute-set>
  <xsl:attribute-set name="section.title.level2.properties">
    <xsl:attribute name="start-indent">0cm</xsl:attribute>
  </xsl:attribute-set>

  <!-- Customizing screen & programlisting -->
  <xsl:attribute-set name="monospace.verbatim.properties">
    <xsl:attribute name="font-family">Lucida Sans Typewriter</xsl:attribute>
    <xsl:attribute name="font-size">9pt</xsl:attribute>
    <xsl:attribute name="wrap-option">wrap</xsl:attribute>
    <xsl:attribute name="hyphenation-character">\</xsl:attribute>
    <xsl:attribute name="keep-together.within-column">always</xsl:attribute>
  </xsl:attribute-set>
  <!-- Shading boxes for verbatim envs -->
  <xsl:param name="shade.verbatim" select="1"/>
  <xsl:attribute-set name="shade.verbatim.style">
    <xsl:attribute name="background-color">#E0E0E0</xsl:attribute>
    <xsl:attribute name="border-width">0.5pt</xsl:attribute>
    <xsl:attribute name="border-style">solid</xsl:attribute>
    <xsl:attribute name="border-color">#575757</xsl:attribute>
    <xsl:attribute name="padding">2pt</xsl:attribute>
  </xsl:attribute-set>

</xsl:stylesheet>