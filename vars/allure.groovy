import org.apache.commons.io.FileExistsException

import java.nio.file.Paths

/**
 * returns formatted map of parameters
 */
def params(args = [:]) {
    def argv = [
            dirs: [],
            name: 'Allure',
            basedir: pwd()
    ] << args

    argv.dirs = (argv.dirs << argv.dir).findAll{it} ?: argv.basedir
    argv.dirs = argv.dirs.collect{
        new File(it).absolute ? it : Paths.get(argv.basedir, it).toString()
    }

    return argv
}

def publish(String dir) {
    publish([dirs:[dir]])
}

/**
 * Publish HTML report with generated by allure
 * @param args named args - name, dir, di   rs
 */
def publish(Map args = [:]) {
    args = params(args)
    def temp = files.tempDir('allure')
    dir(temp) {
        try {
            sh script: "allure generate --report-dir . --clean ${args.dirs.join(' ')}"
            publishHTML(target: [
                    allowMissing         : true,
                    alwaysLinkToLastBuild: false,
                    keepAll              : true,
                    reportDir            : '.',
                    reportFiles          : 'index.html',
                    reportName           : args.name,
            ])
        } finally {
            deleteDir()
        }
    }
}

/**
 * Publish HTML report with generated by allure
 * @param args named args - name, dir, dirs
 */
call(args = [:]) {
    publish(args)
}