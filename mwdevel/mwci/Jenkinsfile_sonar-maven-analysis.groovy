#!groovy
// name: sonar-maven-analysis

properties([
  buildDiscarder(logRotator(numToKeepStr: '10')),
  parameters([
    string(name: 'REPO',   defaultValue: ''),
    string(name: 'BRANCH', defaultValue: ''),
  ])
])

stage('analysis'){
  node('maven'){
    def cobertura_opts = 'cobertura:cobertura -Dmaven.test.failure.ignore -DfailIfNoTests=false -Dcobertura.report.format=xml'
    def checkstyle_opts = 'checkstyle:check -Dcheckstyle.config.location=google_checks.xml'

    withSonarQubeEnv{ sh "mvn clean -U ${cobertura_opts} ${checkstyle_opts} ${SONAR_MAVEN_GOAL} -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN}" }

    dir('target/sonar') {
      stash name: 'sonar-report', include: 'report-task.txt'
    }
  }
}

stage('quality gate'){
  node('generic'){
    unstash 'sonar-report'

    def sonarServerUrl = readProperty('report-task.txt', 'serverUrl')
    def ceTaskUrl = readProperty('report-task.txt', 'ceTaskUrl')
    def sonarBasicAuth

    withSonarQubeEnv{ sonarBasicAuth  = "${SONAR_AUTH_TOKEN}:" }

    timeout(time: 3, unit: 'MINUTES') {
      waitUntil {
        def result = jsonParse(ceTaskUrl, sonarBasicAuth, '.task.status')
        echo "Current CeTask status: ${result}"
        return "SUCCESS" == "${result}"
      }
    }

    def analysisId = jsonParse(ceTaskUrl, sonarBasicAuth, '.task.analysisId')
    echo "Analysis ID: ${analysisId}"

    def url = "${sonarServerUrl}/api/qualitygates/project_status?analysisId=${analysisId}"
    def qualityGate =  jsonParse(url, sonarBasicAuth, '')
    echo "${qualityGate}"

    def status =  jsonParse(url, sonarBasicAuth, '.projectStatus.status')

    if ("ERROR" == "${status}") {
      currentBuild.result = 'UNSTABLE'
    }
  }
}


def readProperty(filename, prop) {
  def value = sh script: "cat ${filename} | grep ${prop} | cut -d'=' -f2-", returnStdout: true
  return value.trim()
}

def jsonParse(url, basicAuth, field) {
  def value =  sh script: "curl -s -u '${basicAuth}' '${url}' | jq -r '${field}'", returnStdout: true
  return value.trim()
}
