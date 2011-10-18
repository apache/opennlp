@ECHO off

REM #   Licensed to the Apache Software Foundation (ASF) under one
REM #   or more contributor license agreements.  See the NOTICE file
REM #   distributed with this work for additional information
REM #   regarding copyright ownership.  The ASF licenses this file
REM #   to you under the Apache License, Version 2.0 (the
REM #   "License"); you may not use this file except in compliance
REM #   with the License.  You may obtain a copy of the License at
REM #
REM #    http://www.apache.org/licenses/LICENSE-2.0
REM #
REM #   Unless required by applicable law or agreed to in writing,
REM #   software distributed under the License is distributed on an
REM #   #  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
REM #   KIND, either express or implied.  See the License for the
REM #   specific language governing permissions and limitations
REM #   under the License.

REM # Note:  Do not output anything in this script file, any output
REM #        may be inadvertantly placed in any output files if
REM #        output redirection is used.

IF "%JAVA_CMD%" == "" (
	IF "%JAVA_HOME%" == "" (
		SET JAVA_CMD=java 
	) ELSE (
		SET JAVA_CMD=%JAVA_HOME%\bin\java
	)
)

REM #  Should work with Windows XP and greater.  If not, specify the path to where it is installed.
IF "%OPENNLP_HOME%" == "" SET OPENNLP_HOME=%~sp0..

%JAVA_CMD% -Xmx4096m -jar %OPENNLP_HOME%\lib\opennlp-tools-*.jar %*
