#!/usr/bin/env groovy

pipeline {
  agent { label 'docker' }

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { 
    upstream(upstreamProjects: 'docker_build-storm-testsuite-image', threshold: hudson.model.Result.SUCCESS)
  }

  environment {
    DOCKER_REGISTRY_HOST = "${env.DOCKER_REGISTRY_HOST}"
    DIRECTORY = "grinder"
  }

  stages {
    stage('prepare'){
      steps {
        container('docker-runner'){
          deleteDir()
          git 'https://github.com/italiangrid/docker-scripts'
          sh "docker pull ${DOCKER_REGISTRY_HOST}/italiangrid/storm-testsuite"
          sh "docker tag ${DOCKER_REGISTRY_HOST}/italiangrid/storm-testsuite italiangrid/storm-testsuite"
        }
      }
    }

    stage('build'){
      steps {
        container('docker-runner'){
          dir("${env.DIRECTORY}"){ 
            sh 'sh build-image.sh' 
          }
        }
      }
    }

    stage('push'){
      steps {
        container('docker-runner'){
          dir("${env.DIRECTORY}"){ 
            sh "docker tag italiangrid/grinder ${DOCKER_REGISTRY_HOST}/italiangrid/grinder"
            sh "docker push ${DOCKER_REGISTRY_HOST}/italiangrid/grinder"
          }
        }
      }
    }
    
    stage('result'){
      steps {
        script { currentBuild.result = 'SUCCESS' }
      }
    }
  }

  post {
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }

    changed {
      script{
        if('SUCCESS'.equals(currentBuild.result)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
