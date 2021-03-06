#!/bin/env groovy

@Grapes([
    @Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.1')
])

import groovy.json.JsonBuilder
import groovyx.net.http.HTTPBuilder

import static java.lang.Math.*
import static groovyx.gpars.GParsPool.*
import static groovyx.gpars.agent.Agent.*
import static org.apache.commons.cli.Option.UNLIMITED_VALUES


/**
 * Created by Huabin on 14-3-11.
 */

// formatters
final PAD_LENGTH_1              = 60
final PAD_LENGTH_2              = 24

// version info
final VERSION_INFO              = 'Swagger JSON Exporter version "1.0.0.20140312"'

// default values
final API_DOCS_PATH             = 'api-docs'
final API_DOCS_URL              = "http://localhost:8080/api/$API_DOCS_PATH"
final STATIC_SUB_API_DOCS_PATH  = 'listing'
final STATIC_SUB_API_DOCS_URL   = "http://localhost:8080/apispec/$STATIC_SUB_API_DOCS_PATH"
final OUTPUT_DIR                = 'output'


def startTime = System.currentTimeMillis()

def cli = new CliBuilder().with {
    h   longOpt: 'help',                    'Print this usage information'
    v   longOpt: 'version',                 'Print version'
    u   longOpt: 'api-docs-url',            "The URL of original swagger api-docs <default: $API_DOCS_URL>",                    args: 1, argName: 'u'
    su  longOpt: 'static-sub-api-docs-url', "The URL of static swagger sub-api-docs URL <default: $STATIC_SUB_API_DOCS_URL>",   args: 1, argName: 'su'
    o   longOpt: 'output-dir',              "The output directory <default: $OUTPUT_DIR>",                                      args: 1, argName: 'o'
    il  longOpt: 'include-list',            'The include-list <default: null>', args: UNLIMITED_VALUES, valueSeparator: ',', argName:  'arg1,arg2,arg3...'


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
def staticSubApiDocsUrl     = options.su    ?: conf?.staticSubApiDocsUrl    ?: STATIC_SUB_API_DOCS_URL
def outputDir               = options.o     ?: conf?.outputDir              ?: OUTPUT_DIR
def includeList             = options.ils   ?: conf?.includeList            ?: null

// print options summary
def optionsSummary = /
${''.padRight PAD_LENGTH_1, '-'}
|${"$VERSION_INFO".center PAD_LENGTH_1-2}|
${''.padRight PAD_LENGTH_1, '-'}
${'apiDocsUrl'.padRight PAD_LENGTH_2} = $apiDocsUrl
${'staticSubApiDocsUrl'.padRight PAD_LENGTH_2} = $staticSubApiDocsUrl
${'outputDir'.padRight PAD_LENGTH_2} = ${(outputDir as File).absolutePath}
${'includeList'.padRight PAD_LENGTH_2} = ${includeList ? includeList.join(',') : null }/

println optionsSummary


// processing logic
def apiDocsHttp = [apiDocsUrl] as HTTPBuilder
def apiDocs = apiDocsHttp.get([:])
apiDocs.apis = !includeList ? apiDocs.apis
        : apiDocs.apis.findAll { api ->
    def path = api.path
    includeList.any { path.contains it }
}

if (!apiDocs.apis) {
    println "<INFO> No entry exported."
} else {

    def subApiDocs = agent([])

    def coreNum = Runtime.runtime.availableProcessors()
    withPool(min(apiDocs.apis.size(), coreNum)) {
        apiDocs.apis.eachParallel { api ->
            def subApiDocHttp = ["$apiDocsUrl/${api.path}"] as HTTPBuilder
            def subApiDoc = subApiDocHttp.get([:])
            subApiDocs << { it << subApiDoc }
            subApiDocHttp.shutdown()
        }
    }
    apiDocsHttp.shutdown()

    // update api-docs
    apiDocs.apis.each { it.path = "$staticSubApiDocsUrl/${it.path}" }
    def staticApiDocsJson = new JsonBuilder(apiDocs).toPrettyString()
    // output api-docs
    def staticApiDocsFile = "$outputDir/$API_DOCS_PATH" as File
    staticApiDocsFile.parentFile.mkdirs() // insure the output dir is existed
    staticApiDocsFile.withWriter {
        def writer = it as BufferedWriter
        writer << "$staticApiDocsJson\n"
        writer.flush()
    }

    // sub-apis
    def staticSubApiDocsPath = staticSubApiDocsUrl.tokenize('/')[-1]
    withPool(min(subApiDocs.val.size(), coreNum)) {
        subApiDocs.val.eachParallel { subApiDoc ->
            def staticSubApiDocJson = new JsonBuilder(subApiDoc).toPrettyString()
            def resPath = subApiDoc.resourcePath
            def subApiDocFile = "$outputDir/$staticSubApiDocsPath/$resPath" as File
            subApiDocFile.parentFile.mkdirs()
            subApiDocFile.withWriter {
                def writer = it as BufferedWriter
                writer << "$staticSubApiDocJson\n"
                writer.flush()
            }
        }
    }
}


println '++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++'
def endTime = System.currentTimeMillis()
def timeInSec = ((endTime - startTime) as Double)/1000
println "<INFO> Job finished at ${new Date().format( 'yyyy-MM-dd HH:mm:ss' )} in ${Math.ceil(timeInSec)} seconds"
println '<STAGE> Fin\n'
