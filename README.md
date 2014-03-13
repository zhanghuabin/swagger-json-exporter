#   Swagger Json Exporter

This is an utility to use to export the JSON of Swagger API docs from web site to local.


##  Prerequisites

*   To run sje.groovy as shell script, you MUST have Groovy environment on local.

    On Mac OSX, Linux and Cygwin, you can use [GVM](http://gvmtool.net) to download and configure any version of you choice.

    Otherwise, you have to download a version of Groovy from [this site](http://groovy.codehaus.org) and configure PATH environment manually.


##  Quickstart

*   Run sje.groovy as shell script

    Copy sje.groovy to any location, then run:

        ./sje.groovy -u http://petstore.swagger.wordnik.com/api/api-docs -su http://localhost:8080/apispec/listings -o /tmp/swagger-api-docs -il pet

    You'll get a subset of petstore APIs which includes only /pet path.

