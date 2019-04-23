Pipeliner
=========

This component is used to define the pipeline concept and run the pipelines in
Jenkins. It processes user inputs to pipelines, configures and executes the
pipeline and provides utility functions. It is writted in [Groovy](http://groovy-lang.org/) and follows 
the structure for a [Jenkins Shared Library](https://jenkins.io/doc/book/pipeline/shared-libraries/).
License information is either explicitly stated in [LICENSE](https://github.com/Daimler/pipeliner/blob/master/LICENSE) file or defaults to 
MIT license.

Why do we need Pipeliner
------------------------

Traditional jenkins jobs are defined as list of steps in the job, which have to
be repeated across jobs.The jobs are static and don't allow possibility of easy
configuration by the user.

Pipeliner defines the pipeline class, which is based on smaller, modular chunks
of functionality, called stages. The stages are reusable across different
pipelines, thus help avoid repitition. These pipelines can take inputs from the
user and adapt accordingly. The stages and pipeline implementation is contained
in a different repository, which is referred to as [Pipeliner-Depot](https://github.com/Daimler/pipeliner-depot).

Directory Structure
-------------------

```
(root)
+-- doc                         # Documentation
|    +-- overview.html
+-- src                         # Pipeliner source files
|   +-- com
|       +-- daimler
|           +-- pipeliner
|               +-- BasePipeline.groovy
|               +-- InputParser.groovy
|               + ...
+-- test                        # Pipeliner test files
|    +-- unit-test
|        +-- BuildTest.groovy   # Unit Test for for source pipelines
|        +-- resources
|            +-- build.jenkins  # Jenkins File that runs source pipeline
|    +-- utils                  # Utility classes to extend testing framework

```

Dependencies
------------
- Groovy 2.4
- Gradle 4.7

If you build/test Pipeliner locally, it is recommended to use sdkman:
```
curl -s get.sdkman.io | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install groovy
sdk install gradle 4.7
```


How to use Pipeliner
----------------------

Pipeliner cannot be used standalone, but as component of the Pipeliner-Depot. 
Please see detailed description and reference example in the Pipeliner-Depot project.


How to build documentation
--------------------------

Documentation is build with GroovyDoc from the inline documentation in the code
and some files in the doc/ directory. To build the documentation run

```shell
gradle groovydoc
```

You will find the documentation in the HTML format under build/docs/groovydoc/index.html

How to test
-----------

The [Jenking Pipeline Unit testing Framework](https://github.com/jenkinsci/JenkinsPipelineUnit) can be used to unit test pipelines.

To run all the tests, run below in the root project directory. This runs all the
tests in the `test` directory. This can also be used while development of
pipelines.

```shell
gradle test
```


References
----------

* [Groovy](http://groovy-lang.org/)
* [Jenkins Shared Library](https://jenkins.io/doc/book/pipeline/shared-libraries/)
* [Using Jenkins Shared Library](https://jenkins.io/doc/book/pipeline/shared-libraries/#using-libraries)
* [Jenkins Shared Library structure](https://jenkins.io/doc/book/pipeline/shared-libraries/#directory-structure)
* [Jenking Pipeline Unit testing Framework](https://github.com/jenkinsci/JenkinsPipelineUnit)
* [Pipeliner-Depot](https://github.com/Daimler/pipeliner-depot)


Provider Information
====================

Please visit <https://mbition.io/en/home/#imprint-privacy> for information on the provider.


NOTICE
======
Before you use the program in productive use, please take all necessary precautions, e.g. testing and verifying the program with regard to your specific use. The program was tested solely for our own use cases, which might differ from yours.
