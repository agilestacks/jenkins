#!/usr/bin/env groovy

import java.nio.file.Files
import java.nio.file.Paths


List<String> findDirs(args=[:]) {
    def argv = [
            basedir: pwd(),
            includes: '**/*',
            absolutePath: true,
    ] << args
    dir(argv.basedir) {
        result = findFiles(glob: argv.includes).
                toList().
                collect {
                    def f = it.directory ? new File(it.path) : new File(it.path).parentFile
                    argv.absolutePath ? f?.absolutePath : f?.path
                }.findAll {it}.unique()
    }
    return result
}

List<String> findDirs(String includes) {
    findDirs(includes: includes)
}

def tempDir(String name, Closure body=null) {
    return call(prefix: name, body)
}

def tempDir(def arg=[:], Closure body=null) {
    def argv = [ prefix: '',
                 directory: pwd(tmp: true),
                 kind: 'absolute',
                 deleteOnExit: true, ]

    if (arg instanceof Map) {
        argv << arg.findAll{it.value != null}
    } else {
        argv.prefix = arg?.toString()
    }

    def parent = new File( argv.directory as String).toPath()
    if ( Files.notExists(parent) ){
        parent.toFile().mkdirs()
    }

    def dir = Files.createTempDirectory(parent, argv.prefix as String)
    def result
    if ('absolute' == argv.kind) {
        result =  dir.toAbsolutePath().toString()
    } else if ('real' == argv.kind) {
        result = dir.toRealPath().toString()
    } else {
        result = dir.toString()
    }

    if (body != null) {
        dir(result) {
            try {
                def config = [:]
                body.resolveStrategy = Closure.DELEGATE_FIRST
                body.delegate = config
                body()
            } catch (all) {
                error all?.message ?: all.toString()
            }

            if (argv.deleteOnExit) {
                deleteDir()
            }
        }
    }

    return result
}


def tempFile(String... args) {
    def list = (args as List)
    return call(path: list[0], extension: list[1])
}

def tempFile(Map arg=[:]) {
    String tempDir = pwd(temp: true)
    def argv = [ path: tempDir,
                 extension: '.tmp',
                 kind: 'absolute',
                 deleteOnExit: false ] << arg.findAll{it.value != null}

    def path = Paths.get(argv.path.toString())
    def directory = path
    def filename = ""
    if ( !path.toFile().isDirectory() ) {
        directory =  path.parent ?: Paths.get( tempDir )
        filename = path.fileName.toString()
    }

    if (!Files.exists(directory)) {
        Files.createDirectories(directory)
    }

    def temp = Files.createTempFile(directory, filename, argv.extension.toString())
    if (argv.deleteOnExit) {
        temp.toFile().deleteOnExit()
    }

    if ('absolute' == argv.kind) {
        temp = temp.toAbsolutePath()
    } else if ('real' == argv.kind) {
        temp = temp.toRealPath()
    }

    return temp.toString()
}