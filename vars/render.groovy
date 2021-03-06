#!/usr/bin/env groovy

import com.agilestacks.jenkins.share.StringReplace
import com.agilestacks.jenkins.share.GroovyTemplate

def curly(String text, Map args=[:]) {
    return new StringReplace().curly(text, args)
}

def mustache(String text, Map args=[:]) {
    return new StringReplace().mustache(text, args)
}

def template(String text, Map args=[:]) {
    return new GroovyTemplate().render(text, args)
}

def call(String text, Map args=[:]) {
	//TODO make defaults configurable
    return template(text, args)
}
