#!/usr/bin/env groovy

def call(def log) {
    def paramFormat = /([\d\w-_\.]+) = (.*)/

    return log
            .split('Outputs:')[1]
            .tokenize('\n')
            .collect { it =~ paramFormat }
            .findAll { it.matches() }
            .collectEntries { [(it.group(1)):it.group(2)] }
}
