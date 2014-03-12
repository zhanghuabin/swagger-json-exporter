#!/bin/env groovy

@Grapes([
    @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')
])

import groovy.json.JsonBuilder
import groovyx.net.http.HTTPBuilder

import static java.lang.Math.*
import static groovyx.gpars.GParsPool.*
import static groovyx.gpars.agent.Agent.*


/**
 * Created by Huabin on 14-3-11.
 */

// formatters
final PAD_LENGTH_1              = 60
final PAD_LENGTH_2              = 24

// version info
final VERSION_INFO              = 'Swagger JSON Exporter version "1.0.0.20140217"'

// default values
final API_DOCS_URL              = 'http://localhost:8080/api/api-docs'
final STATIC_SUB_API_DOCS_URL   = 'http://localhost:8080/apispec/listing'
final OUTPUT_DIR                = 'output'


def startTime = System.currentTimeMillis()

def cli = new CliBuilder().with {
    h   longOpt: 'help',                    'Print this usage information'
    v   longOpt: 'version',                 'Print version'
    u   longOpt: 'api-docs-url',            "The URL of original swagger api-docs <default: $API_DOCS_URL>",                    args: 1, argName: 'u'
    su  longOpt: 'static-sub-api-docs-url', "The URL of static swagger sub-api-docs URL <default: $STATIC_SUB_API_DOCS_URL>",   args: 1, argName: 'su'
    o   longOpt: 'output-dir',              "The output directory <default: $OUTPUT_DIR>",                                      args: 1, argName: 'o'


    usage = 'sje [options]'
    header = """$VERSION_INFO
Options:"""
    footer = """
Report bugs to: digitarts@gmail.com"""

    formatter.leftPadding = 4
    formatter.syntaxPrefix = 'Usage: '
    width = formatter.width = 200

    it
}

def options = cli.parse args
if (!options) return -1
if (options.h) { cli.usage(); return }
if (options.v) { println VERSION_INFO; return }

def conf
def confFile = 'sje.gconf' as File
if (confFile.exists()) {
    conf = new ConfigSlurper().parse confFile.toURI().toURL()
}

def apiDocsUrl              = options.u     ?: conf?.apiDocsUrl             ?: API_DOCS_URL
def staticSubApiDocsUrl     = options.su    ?: conf?.staticSubApiDocsUrl   ?: STATIC_SUB_API_DOCS_URL
def outputDir               = options.o     ?: conf?.outputDir              ?: OUTPUT_DIR

// print options summary
def optionsSummary =
"""${''.padRight PAD_LENGTH_1, '-'}
|${"$VERSION_INFO".center PAD_LENGTH_1-2}|
${''.padRight PAD_LENGTH_1, '-'}
${'apiDocsUrl'.padRight PAD_LENGTH_2} = $apiDocsUrl
${'staticSubApiDocsUrl'.padRight PAD_LENGTH_2} = $staticSubApiDocsUrl
${'outputDir'.padRight PAD_LENGTH_2} = ${(outputDir as File).absolutePath}
"""
println optionsSummary


// processing logic
def apiDocsHttp = [apiDocsUrl] as HTTPBuilder
def apiDocs = apiDocsHttp.get([:])
def apiDocs_apis = apiDocs.apis
def subApiDocs = agent([])

withPool(min(apiDocs_apis.size(), Runtime.runtime.availableProcessors())) {
    apiDocs_apis.eachParallel { api ->
        def subApiDocHttp = ["$apiDocsUrl/${api.path}"] as HTTPBuilder
        def subApiDoc = subApiDocHttp.get([:])
        subApiDocs << { it << subApiDoc }
        subApiDocHttp.shutdown()
    }
}
apiDocsHttp.shutdown()

//println new JsonBuilder(apiDocs).toPrettyString()
//subApiDocs.val.each { println new JsonBuilder(it).toPrettyString() }

// update api-docs
apiDocs_apis.each { it.path = "$staticSubApiDocsUrl/${it.path}" }
def staticApiDocsJson = new JsonBuilder(apiDocs).toPrettyString()
// output api-docs
def staticApiDocsFile = "$outputDir/api-docs" as File
staticApiDocsFile.parentFile.mkdirs() // insure the output dir is existed
staticApiDocsFile.withWriter {
    def writer = it as BufferedWriter
    writer << "$staticApiDocsJson\n"
    writer.flush()
}

// sub-apis
withPool(min(subApiDocs.val.size(), Runtime.runtime.availableProcessors())) {
    subApiDocs.val.eachParallel { subApiDoc ->
        def staticSubApiDocJson = new JsonBuilder(subApiDoc).toPrettyString()
        def resPath = subApiDoc.resourcePath
        def subApiDocFile = "$outputDir/listing/$resPath" as File
        subApiDocFile.parentFile.mkdirs()
        subApiDocFile.withWriter {
            def writer = it as BufferedWriter
            writer << "$staticSubApiDocJson\n"
            writer.flush()
        }
    }
}


println '++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++'
def endTime = System.currentTimeMillis()
def timeInSec = ((endTime - startTime) as Double)/1000
println "<INFO> Job finished at ${new Date().format( 'yyyy-MM-dd HH:mm:ss' )} in ${Math.ceil(timeInSec)} seconds"
println '<STAGE> Fin\n'
