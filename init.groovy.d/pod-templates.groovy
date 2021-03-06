#!/usr/bin/env groovy

import java.util.logging.Logger
import jenkins.*
import jenkins.model.*
import hudson.*
import hudson.model.*
import org.csanchez.jenkins.plugins.kubernetes.*
import org.csanchez.jenkins.plugins.kubernetes.model.*
import org.csanchez.jenkins.plugins.kubernetes.volumes.*
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.*
import io.fabric8.kubernetes.client.*

import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.plugins.credentials.impl.*

def DOCKER_ARGS = '--host=unix:///var/run/docker.sock --host=tcp://0.0.0.0:2375 --storage-driver=overlay'

def log = Logger.getLogger(this.class.name)
def jenk     = Jenkins.instance

if ( jenk.clouds.find { it.name == 'kubernetes' } ) {
  log.warning 'It appears "kubernetes" cloud already configured'
  log.info 'Doing nothing...'
  return
}

def k8sHost  = System.getenv('KUBERNETES_SERVICE_HOST')
def k8sPort  = System.getenv('KUBERNETES_SERVICE_PORT') ?: '443'
def creds    = jenk.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0]
def k8sCreds = creds.credentials.find {it instanceof TokenProducer}
if (k8sCreds == null) {
  k8sCreds  = new ServiceAccountCredential(
    CredentialsScope.GLOBAL,
    'thiscluster',
    'Default service account for Kubrnetes'
  )
  creds.store.addCredentials(Domain.global(), k8sCreds)
}

def configVars = [
  BACKEND_BUCKET_NAME:   'unset',
  BACKEND_BUCKET_REGION: 'us-east-1',
  BASE_DOMAIN:           'superhub.io',
  DNS_NAME:              'unset',
  AWS_REGION:            'us-east-1'
]

def client = new DefaultKubernetesClient(new ConfigBuilder().build())
if (client.masterUrl && client.namespace) {
  client.
    configMaps().
    withLabels([
      'project': 'jenkins',
      'qualifier': 'env-config'
    ]).
    list().
    items.each {
      configVars.putAll( it.data ?: [:] )
    }
}

def configapStorage = client.
    configMaps().
    withLabels([
        project: 'jenkins',
        qualifier: 'storage-config'
    ]).list().items[0]?.data

def jenkHost  = System.getenv('JENKINS_SERVICE_HOST')
def jenkPort  = System.getenv('JENKINS_SERVICE_PORT') ?: '8080'
def jenkJnlp  = System.getenv('JENKINS_SERVICE_JNLP') ?: '50000'

def namespaceFile  = new File('/var/run/secrets/kubernetes.io/serviceaccount/namespace')
def kube           = new KubernetesCloud('kubernetes')
kube.serverUrl     = "${k8sPort == '443' ? 'https' : 'http'}://${k8sHost}:${k8sPort}/"
kube.skipTlsVerify = kube.serverUrl.startsWith('https')
kube.namespace     = namespaceFile.exists() ? namespaceFile.text : "default"
kube.credentialsId = k8sCreds.id
kube.jenkinsUrl    = "${jenkPort == '443' ? 'https' : 'http'}://${jenkHost}:${jenkPort}/"
kube.jenkinsTunnel = "${jenkHost}:${jenkJnlp}"
kube.defaultsProviderTemplate = 'default'

// def container        = new ContainerTemplate('jnlp', 'jenkinsci/jnlp-slave:alpine')
def jnlp             = new ContainerTemplate('jnlp', 'docker.io/agilestacks/jenkins-slave:2.46.1-1')
jnlp.command         = ''
jnlp.args            = '${computer.jnlpmac} ${computer.name}'
jnlp.ttyEnabled      = false
jnlp.privileged      = false
jnlp.alwaysPullImage = true

def toolbox             = new ContainerTemplate('toolbox', 'docker.io/agilestacks/toolbox:stable')
toolbox.command         = 'cat'
toolbox.args            = ''
toolbox.ttyEnabled      = true
toolbox.privileged      = true
toolbox.alwaysPullImage = true
toolbox.envVars         = [ new ContainerEnvVar('BACKEND_BUCKET_NAME', configVars.BACKEND_BUCKET_NAME),
                            new ContainerEnvVar('BACKEND_BUCKET_REGION', configVars.BACKEND_BUCKET_REGION),
                            new ContainerEnvVar('BASE_DOMAIN', configVars.BASE_DOMAIN),
                            new ContainerEnvVar('DNS_NAME', configVars.DNS_NAME),
                            new ContainerEnvVar('DOCKER_DAEMON_ARGS', "-D ${DOCKER_ARGS}"),
                            new ContainerEnvVar('AWS_REGION', configVars.AWS_REGION) ]

def pod1         = new PodTemplate()
pod1.name        = 'default'
pod1.label       = pod1.name
pod1.namespace   = client.namespace
pod1.annotations = [
  new PodAnnotation("provider", "agilestacks.com"),
  new PodAnnotation("project",  "jenkins"),
  new PodAnnotation("qualifier", "pod"),
  new PodAnnotation("type", "jenkins-node")
]
pod1.volumes = [
  // new EmptyDirVolume('/home/jenkins', false),
  // new PersistentVolumeClaim('/home/jenkins/workspace', 'workspace-volume', false),
]
pod1.containers  = [ jnlp ]

def pod2Volumes = []
pod2Volumes.add(new HostPathVolume("/var/run/docker.sock", "/var/run/docker.sock"))
pod2Volumes.add(new EmptyDirVolume('/var/lib/docker', false))

def BUCKET_KIND =  configapStorage?.BUCKET_KIND ?: ''
def NFS_PVC_NAME =  configapStorage?.NFS_PVC_NAME ?: ''

if(BUCKET_KIND.trim() == ("ceph") && !NFS_PVC_NAME.trim().isEmpty() ){
    pod2Volumes.add(new PersistentVolumeClaim('/home/jenkins/workspace', NFS_PVC_NAME, false))
}

def pod2         = new PodTemplate()
pod2.name        = 'toolbox'
pod2.label       = pod2.name
pod2.namespace   = client.namespace
pod2.inheritFrom = pod1.name
pod2.containers  = [ toolbox ]
pod2.volumes     = pod2Volumes
kube.templates  = [ pod1, pod2 ]
jenk.clouds << kube
jenk.save()
log.info '"kubernetes" cloud has been configured successfully'
