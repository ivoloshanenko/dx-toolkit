#!/usr/bin/env python2.7
#
# Copyright (C) 2013-2015 DNAnexus, Inc.
#
# This file is part of dx-toolkit (DNAnexus platform client libraries).
#
#   Licensed under the Apache License, Version 2.0 (the "License"); you may not
#   use this file except in compliance with the License. You may obtain a copy
#   of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
#   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
#   License for the specific language governing permissions and limitations
#   under the License.

import sys, json

preamble = '''# Do not modify this file by hand.
#
# It is automatically generated by src/api_wrappers/generateRAPIWrappers.py.
# (Run make api_wrappers to update it.)'''

class_method_template = '''
##' {method_name} API wrapper
##'
##' This function makes an API call to the \code{{{route}}} API
##' method; it is a simple wrapper around the \code{{\link{{dxHTTPRequest}}}}
##' function which makes POST HTTP requests to the API server.
##'
##' 
##' @param inputParams Either an R object that will be converted into JSON
##' using \code{{RJSONIO::toJSON}} to be used as the input to the API call.  If
##' providing the JSON string directly, you must set \code{{jsonifyData}} to
##' \code{{FALSE}}.
##' @param jsonifyData Whether to call \code{{RJSONIO::toJSON}} on
##' \code{{inputParams}} to create the JSON string or pass through the value of
##' \code{{inputParams}} directly.  (Default is \code{{TRUE}}.)
##' @param alwaysRetry Whether to always retry even when no response is
##' received from the API server
##' @return If the API call is successful, the parsed JSON of the API server
##' response is returned (using \code{{RJSONIO::fromJSON}}).
##' @export
##' @seealso \code{{\link{{dxHTTPRequest}}}}{wiki_ref}
{method_name} <- function(inputParams=emptyNamedList,
{indent}jsonifyData=TRUE,
{indent}alwaysRetry={retry}) {{
  dxHTTPRequest('{route}', inputParams, jsonifyData=jsonifyData, alwaysRetry=alwaysRetry)
}}'''

object_method_template = '''
##' {method_name} API wrapper
##'
##' This function makes an API call to the \code{{{route}}} API
##' method; it is a simple wrapper around the \code{{\link{{dxHTTPRequest}}}}
##' function which makes POST HTTP requests to the API server.
##'
##' 
##' @param objectID DNAnexus object ID
##' @param inputParams Either an R object that will be converted into JSON
##' using \code{{RJSONIO::toJSON}} to be used as the input to the API call.  If
##' providing the JSON string directly, you must set \code{{jsonifyData}} to
##' \code{{FALSE}}.
##' @param jsonifyData Whether to call \code{{RJSONIO::toJSON}} on
##' \code{{inputParams}} to create the JSON string or pass through the value of
##' \code{{inputParams}} directly.  (Default is \code{{TRUE}}.)
##' @param alwaysRetry Whether to always retry even when no response is
##' received from the API server
##' @return If the API call is successful, the parsed JSON of the API server
##' response is returned (using \code{{RJSONIO::fromJSON}}).
##' @export
##' @seealso \code{{\link{{dxHTTPRequest}}}}{wiki_ref}
{method_name} <- function(objectID,
{indent}inputParams=emptyNamedList,
{indent}jsonifyData=TRUE,
{indent}alwaysRetry={retry}) {{
  resource <- paste('/', objectID, '/', '{method_route}', sep='')
  dxHTTPRequest(resource,
                inputParams,
                jsonifyData=jsonifyData,
                alwaysRetry=alwaysRetry)
}}'''

app_object_method_template = '''
##' {method_name} API wrapper
##'
##' This function makes an API call to the \code{{{route}}} API
##' method; it is a simple wrapper around the \code{{\link{{dxHTTPRequest}}}}
##' function which makes POST HTTP requests to the API server.
##'
##' 
##' @param appNameOrID An app identifier using either the name of an app
##' ("app-name") or its full ID ("app-xxxx")
##' @param alias If an app name is given for \code{{appNameOrID}}, this can be
##' provided to specify a version or tag (if not provided, the "default" tag is
##' used).
##' @param inputParams Either an R object that will be converted into JSON
##' using \code{{RJSONIO::toJSON}} to be used as the input to the API call.  If
##' providing the JSON string directly, you must set \code{{jsonifyData}} to
##' \code{{FALSE}}.
##' @param jsonifyData Whether to call \code{{RJSONIO::toJSON}} on
##' \code{{inputParams}} to create the JSON string or pass through the value of
##' \code{{inputParams}} directly.  (Default is \code{{TRUE}}.)
##' @param alwaysRetry Whether to always retry even when no response is
##' received from the API server
##' @return If the API call is successful, the parsed JSON of the API server
##' response is returned (using \code{{RJSONIO::fromJSON}}).
##' @export
##' @seealso \code{{\link{{dxHTTPRequest}}}}{wiki_ref}
{method_name} <- function(appNameOrID, alias=NULL,
{indent}inputParams=emptyNamedList, jsonifyData=TRUE,
{indent}alwaysRetry={retry}) {{
  if (is.null(alias)) {{
    fullyQualifiedVersion <- paste('/', appNameOrID, sep='')
  }} else {{
    fullyQualifiedVersion <- paste('/', appNameOrID, '/', alias, sep='')
  }}
  resource <- paste(fullyQualifiedVersion, '/', '{method_route}', sep='')
  dxHTTPRequest(resource,
                inputParams,
                jsonifyData,
                alwaysRetry=alwaysRetry)
}}'''

print preamble

for method in json.loads(sys.stdin.read()):
    route, signature, opts = method
    method_name = signature.split("(")[0]
    retry = "TRUE" if (opts['retryable']) else "FALSE"
    wiki_ref = "" if (opts["wikiLink"] is None) else "\n##' @references API spec documentation: \url{" + opts["wikiLink"].replace(" ", "%20").replace("%", "\\%") + "}"
    indent = " " * len(method_name + " <- function(")
    if (opts['objectMethod']):
        root, oid_route, method_route = route.split("/")
        if oid_route == 'app-xxxx':
            print app_object_method_template.format(method_name=method_name,
                                                    route=route,
                                                    method_route=method_route,
                                                    retry=retry,
                                                    wiki_ref=wiki_ref,
                                                    indent=indent)
        else:
            print object_method_template.format(method_name=method_name,
                                                route=route,
                                                method_route=method_route,
                                                retry=retry,
                                                wiki_ref=wiki_ref,
                                                indent=indent)
    else:
        print class_method_template.format(method_name=method_name,
                                           route=route,
                                           retry=retry,
                                           wiki_ref=wiki_ref,
                                           indent=indent)
