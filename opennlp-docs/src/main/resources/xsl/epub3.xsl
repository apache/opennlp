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

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:date="http://exslt.org/dates-and-times"
                xmlns:h="http://www.w3.org/1999/xhtml"
                xmlns="http://www.w3.org/1999/xhtml"
                extension-element-prefixes="date"
                exclude-result-prefixes="date h">
  
  <xsl:import href="urn:docbkx:stylesheet"/>

  <!-- Workaround to create working epub3 files, see: https://github.com/mimil/docbkx-tools/issues/130 -->
  <xsl:variable name="epub.oebps.dir" select="'./'"/>

  <!-- Id to use to reference cover image -->
  <xsl:param name="epub.cover.image.id" select="'cover-image'"/>

  <!-- ID to use to reference cover filename -->
  <xsl:param name="epub.cover.html.id" select="'cover'"/>

  <xsl:param name="epub.cover.linear" select="1" />

  <!-- Reduce metadata duplication -->
  <xsl:param name="epub.include.optional.metadata.dc.elements" select="1"/>

</xsl:stylesheet>