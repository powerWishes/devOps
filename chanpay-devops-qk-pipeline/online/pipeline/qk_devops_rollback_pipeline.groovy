#!groovy
def globalAppVersion = "1.0.0";
def tarName = "1.0.0.tar"
def needBuildProList = []
def needBuildDmzCoreProList = []
def needBuildDmzGeneralProList = []
def needBuildApCoreProList = []
def needBuildApGeneralProList = []
def needBuildDmzOutProList = []
def needBuildDmzPriProList = []
def needBuildApMiddlewareProList = []
def stopSleepSeconds = 90
def shutdownSleepSeconds = 15
def backupSleepSeconds = 1
def unzipSleepSeconds = 1
def publishSleepSeconds = 30
//检出代码
def checkoutCode(scmUrl,scmCertId,scmDir,scmBranch='master'){
    return checkout(
            [
                    $class: "GitSCM",
                    branches: [[name: "${scmBranch}"]], \
			doGenerateSubmoduleConfigurations: false, extensions: [ \
				[$class: 'RelativeTargetDirectory', relativeTargetDir: "${scmDir}"]
            ], \
				submoduleCfg: [], \
				userRemoteConfigs: [
                    [
                            credentialsId: "${scmCertId}", \
					url: "${scmUrl}" \
				] \
			 ] \
		 ] \
	)
}
def sendFileToServer(serverName,filePath,serverFilePath,removePrefix,execCmd){
    sshPublisher(
            publishers:[
                    sshPublisherDesc(
                            configName: "${serverName}",
                            transfers:
                                    [
                                            sshTransfer(
                                                    cleanRemote: false,
                                                    excludes: '',
                                                    execCommand: """
								${execCmd}
							""",
                                                    execTimeout: 72000000,
                                                    flatten: false,
                                                    makeEmptyDirs: false,
                                                    noDefaultExcludes: false,
                                                    patternSeparator: '[, ]+',
                                                    remoteDirectory: "${serverFilePath}",
                                                    remoteDirectorySDF: false,
                                                    removePrefix: "${removePrefix}",
                                                    sourceFiles: "${filePath}"
                                            )
                                    ],
                            usePromotionTimestamp: false,
                            useWorkspaceInPromotion: false,
                            verbose: false
                    )
            ]
    )
}
node {
    appVersions = sh (script: 'cd /data/online_tar/qk/ && ls -1t', returnStdout: true).trim()
    selectAppTar = null
    selectAppVersion = null
    needPublishZoneList = []
}
pipeline {
    agent any
    //环境变量
    environment {
        basicGitUrl = 'http://10.255.0.110:9090'
        parentProName = 'chanpay-parent'
        parentGeneralProName = 'chanpay-general-parent'
        commonProName = 'chanpay-common'
        facadeProName = 'chanpay-facade'
        facadeClientProName = 'chanpay-facade-client'
        devOpsProGitUrl = '/devops-group/qk-devops.git'
        devOpsProCertId = 'ed1bf91c-4079-4385-abe8-c92c6db9c716'
        // 构建命令
        buildCommand = 'mvn clean install'
    }
    options {
        disableConcurrentBuilds()
        skipDefaultCheckout()
        timeout(time: 1, unit: 'HOURS')
        timestamps()
    }
    stages {
        stage('初始化流水线') {
            input {
                message "请选择需要回滚的版本："
                parameters {
                    choice(
                            name: 'appVersion',
                            choices: "${appVersions}"
                    )
                }
            }
            steps {
                echo '开始初始化流水线!'
                println appVersion
                script {
                    checkoutCode(basicGitUrl + devOpsProGitUrl, devOpsProCertId, "chanpay-devops-qk-pipeline", "master")
                    appInfoList = readJSON file: 'chanpay-devops-qk-pipeline/chanpay-devops-qk-pipeline/online/config/app_info.json'
                    serverInfoList = readJSON file: 'chanpay-devops-qk-pipeline/chanpay-devops-qk-pipeline/online/config/server_info.json'
                    selectAppTar = sh(script: "cd /data/online_tar/qk/${appVersion} && ls *.tar -1t", returnStdout: true).trim()
                    selectAppVersion = appVersion
                    println "selectAppTar:" + selectAppTar
                    println "selectAppVersion:" + selectAppVersion
                }
                echo '完成初始化流水线!'
            }
        }
        stage("读取程序包信息") {
            steps {
                echo "开始读取${selectAppTar}!"
                //读取程序包
                script {
                    needPublishZoneStr = sh(script: "cd /data/online_tar/qk/${selectAppVersion}/${selectAppVersion}/ && ls -1t", returnStdout: true)
                    needPublishZoneList = needPublishZoneStr.split("\n")
                }
                echo "完成读取信息${selectAppTar}!"
            }
        }

        stage("回滚程序") {
            stage("回滚程序") {
                steps {
                    echo "开始回滚程序!"
                    script {
                        needPublishAllAppInfoList = []
                        replica = serverInfoList["replica"]
                        int currentReplica = 0
                        while (currentReplica < replica) {
                            for (needPublishZone in needPublishZoneList) {
                                echo "回滚${needPublishZone}程序"
                                needPublishZoneServerList = serverInfoList[needPublishZone]
                                //当前副本数已经大于该区域服务器总数
                                if (currentReplica + 1 > needPublishZoneServerList.size()) {
                                    continue
                                }
                                needPublishZoneServer = needPublishZoneServerList[currentReplica]
                                echo "暂停${needPublishZoneServer}服务器应用程序"
                                stopCommand = "";
                                needPublishAppStr = sh(script: "cd /data/online_tar/qk/${selectAppVersion}/${selectAppVersion}/${needPublishZone} && ls -1t", returnStdout: true)
                                needPublishAppList = needPublishAppStr.split("\n")
                                needPublishAppInfoList = []
                                for (needPubshApp in needPublishAppList) {
                                    needPublishAppInfoList.add(appInfoList[needPubshApp.substring(0, needPubshApp.indexOf("${selectAppVersion}") - 1)])
                                }

                                for (needPublishAppInfo in needPublishAppInfoList) {
                                    if (needPublishAppInfo.singleNode == null || (needPublishAppInfo.singleNode != null && needPublishAppInfo.singleNode == needPublishZoneServer)) {
                                        stopCommand += "curl -f http://127.0.0.1:${needPublishAppInfo.managerPort}/admin/stop \n"
                                    }
                                }
                                echo "暂停程序：${needPublishZone}/${needPublishZoneServer}\n${needPublishAppList}"

                                stopCommand += "pwd"
                                sendFileToServer("${needPublishZoneServer}",
                                        "",
                                        "",
                                        "",
                                        "${stopCommand}")
                            }
                            sleep stopSleepSeconds

                            for (needPublishZone in needPublishZoneList) {
                                //停止服务
                                echo "停止${needPublishZoneServer}服务器应用程序"
                                needPublishZoneServerList = serverInfoList[needPublishZone]
                                //当前副本数已经大于该区域服务器总数
                                if (currentReplica + 1 > needPublishZoneServerList.size()) {
                                    continue
                                }
                                needPublishZoneServer = needPublishZoneServerList[currentReplica]
                                shutdownCommand = "";
                                needPublishAppStr = sh(script: "cd /data/online_tar/qk/${selectAppVersion}/${selectAppVersion}/${needPublishZone} && ls -1t", returnStdout: true)
                                needPublishAppList = needPublishAppStr.split("\n")
                                needPublishAppInfoList = []
                                for (needPubshApp in needPublishAppList) {
                                    needPublishAppInfoList.add(appInfoList[needPubshApp.substring(0, needPubshApp.indexOf("${selectAppVersion}") - 1)])
                                }

                                for (needPublishAppInfo in needPublishAppInfoList) {
                                    if (needPublishAppInfo.singleNode == null || (needPublishAppInfo.singleNode != null && needPublishAppInfo.singleNode == needPublishZoneServer)) {
                                        shutdownCommand += "curl -f http://127.0.0.1:${needPublishAppInfo.managerPort}/admin/shutdown \n"
                                    }
                                }
                                echo "停止程序：${needPublishZone}/${needPublishZoneServer}\n${needPublishAppList}"

                                shutdownCommand += "pwd"
                                sendFileToServer("${needPublishZoneServer}",
                                        "",
                                        "",
                                        "",
                                        "${shutdownCommand}")
                            }
                            sleep shutdownSleepSeconds


                            for (needPublishZone in needPublishZoneList) {
                                //回滚备份服务
                                echo "回滚备份${needPublishZoneServer}服务器应用程序"
                                needPublishZoneServerList = serverInfoList[needPublishZone]
                                //当前副本数已经大于该区域服务器总数
                                if (currentReplica + 1 > needPublishZoneServerList.size()) {
                                    continue
                                }
                                needPublishZoneServer = needPublishZoneServerList[currentReplica]
                                rollbackCommand = "";
                                needPublishAppStr = sh(script: "cd /data/online_tar/qk/${selectAppVersion}/${selectAppVersion}/${needPublishZone} && ls -1t", returnStdout: true)
                                needPublishAppList = needPublishAppStr.split("\n")
                                needPublishAppInfoList = []
                                for (needPubshApp in needPublishAppList) {
                                    needPublishAppInfoList.add(appInfoList[needPubshApp.substring(0, needPubshApp.indexOf("${selectAppVersion}") - 1)])
                                }

                                for (needPublishAppInfo in needPublishAppInfoList) {
                                    if (needPublishAppInfo.singleNode == null || (needPublishAppInfo.singleNode != null && needPublishAppInfo.singleNode == needPublishZoneServer)) {
                                        rollbackCommand += "cp -r /data/qk_backup_jar/${selectAppVersion}/${needPublishAppInfo.name}/* /opt/qk_app/${needPublishAppInfo.name}/ \n"
                                    }
                                }
                                echo "回滚备份程序：${needPublishZone}/${needPublishZoneServer}\n${needPublishAppList}"

                                rollbackCommand += "pwd"
                                sendFileToServer("${needPublishZoneServer}",
                                        "",
                                        "",
                                        "",
                                        "${rollbackCommand}")
                            }
                            sleep backupSleepSeconds


                            for (needPublishZone in needPublishZoneList) {
                                //启动服务
                                echo "解压${needPublishZoneServer}服务器应用程序"
                                needPublishZoneServerList = serverInfoList[needPublishZone]
                                //当前副本数已经大于该区域服务器总数
                                if (currentReplica + 1 > needPublishZoneServerList.size()) {
                                    continue
                                }
                                needPublishZoneServer = needPublishZoneServerList[currentReplica]

                                //启动服务
                                echo "启动${needPublishZoneServer}服务器应用程序"
                                publishCommand = "";
                                needPublishAppStr = sh(script: "cd /data/online_tar/qk/${selectAppVersion}/${selectAppVersion}/${needPublishZone} && ls -1t", returnStdout: true)
                                needPublishAppList = needPublishAppStr.split("\n")
                                needPublishAppInfoList = []
                                for (needPubshApp in needPublishAppList) {
                                    needPublishAppInfo = appInfoList[needPubshApp.substring(0, needPubshApp.indexOf("${selectAppVersion}") - 1)]
                                    if (needPublishAppInfo.singleNode == null || (needPublishAppInfo.singleNode != null && needPublishAppInfo.singleNode == needPublishZoneServer)) {
                                        needPublishAppName = needPubshApp.substring(0, needPubshApp.indexOf("${selectAppVersion}") - 1)
                                        publishCommand += "cd /opt/qk_app/${needPublishAppName} \n"
                                        publishCommand += "./startup.sh \n"
                                    }
                                }
                                echo "启动程序：${needPublishZone}/${needPublishZoneServer}\n${needPublishAppList}"

                                sendFileToServer("${needPublishZoneServer}",
                                        "",
                                        "",
                                        "",
                                        "${publishCommand}")
                            }
                            sleep publishSleepSeconds
                            currentReplica = currentReplica + 1
                        }
                    }
                    echo "完成回滚程序!"
                }
            }
        }
    }
}