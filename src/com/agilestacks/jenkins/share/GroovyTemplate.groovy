#!/usr/bin/env groovy
package com.agilestacks.jenkins.share

import groovy.text.*


class GroovyTemplate implements Serializable {

    def splitByDots(params=[:]) {
        def props = new Properties()
        props.putAll(params)
        return new ConfigSlurper().parse(props)
    }

    def render(text, params=[:]) {
        def splitted = splitByDots(params)
        def engine   = new SimpleTemplateEngine()
        def template = engine.createTemplate(text).make(splitted)
        return template.toString()
    }
}
