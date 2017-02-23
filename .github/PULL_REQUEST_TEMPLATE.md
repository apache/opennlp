Thank you for contributing to Apache OpenNLP.

In order to streamline the review of the contribution we ask you
to ensure the following steps have been taken:

### For all changes:
- [ ] Is there a JIRA ticket associated with this PR? Is it referenced 
     in the commit message?

- [ ] Does your PR title start with OPENNLP-XXXX where XXXX is the JIRA number you are trying to resolve? Pay particular attention to the hyphen "-" character.

- [ ] Has your PR been rebased against the latest commit within the target branch (typically master)?

- [ ] Is your initial contribution a single, squashed commit?

### For code changes:
- [ ] Have you ensured that the full suite of tests is executed via mvn clean install at the root opennlp folder?
- [ ] Have you written or updated unit tests to verify your changes?
- [ ] If adding new dependencies to the code, are these dependencies licensed in a way that is compatible for inclusion under [ASF 2.0](http://www.apache.org/legal/resolved.html#category-a)? 
- [ ] If applicable, have you updated the LICENSE file, including the main LICENSE file in opennlp folder?
- [ ] If applicable, have you updated the NOTICE file, including the main NOTICE file found in opennlp folder?

### For documentation related changes:
- [ ] Have you ensured that format looks appropriate for the output in which it is rendered?

### Note:
Please ensure that once the PR is submitted, you check travis-ci for build issues and submit an update to your PR as soon as possible.
