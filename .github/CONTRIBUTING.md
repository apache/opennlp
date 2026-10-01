# How to contribute to Apache OpenNLP

Thank you for your intention to contribute to the Apache OpenNLP project. As an open-source community, we highly appreciate external contributions to our project.

To make the process smooth for the project *committers* (those who review and accept changes) and *contributors* (those who propose new changes via pull requests), there are a few rules to follow.

## Contribution Guidelines

Please check out the [How to get involved](http://opennlp.apache.org/get-involved.html) to understand how contributions are made. 
A detailed list of coding standards can be found at [Apache OpenNLP Code Conventions](http://opennlp.apache.org/code-conventions.html) which also contains a list of coding guidelines that you should follow.
For pull requests, there is a [check list](PULL_REQUEST_TEMPLATE.md) with criteria for acceptable contributions.

## Regular expressions in production code

OpenNLP production code scans text with code-point loops and the helpers in `opennlp.tools.util.StringUtil` (for example `splitOnUnicodeWhitespace` and `split(CharSequence, char)`) instead of `java.util.regex`. The build enforces this with forbiddenapis: `dev/forbidden-regex.txt` lists the forbidden signatures, and the `forbidden-regex-main` execution in the root `pom.xml` checks production classes in the `process-classes` phase (tests are not checked). An exception exists only for a member with a documented public contract that is a user-supplied regular expression, such as the patterns given to `RegexNameFinder`; mark that member (not the class) with `@opennlp.tools.commons.SuppressForbidden("reason")`. A private pattern, or one that is an implementation detail, is converted to a scan, not annotated: the check's report is the list of what remains to convert. See OPENNLP-1935 for the background.
