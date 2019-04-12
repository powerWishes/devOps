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
def stopSleepSeconds = 5
def shutdownSleepSeconds = 5
def backupSleepSeconds = 5
def unzipSleepSeconds = 5
def publishSleepSeconds = 5
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
							sourceFiles: "${filePath}",
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
	// 工具集
	tools {
		maven 'maven_3.6.0'
		//jdk 'jdk_1.8.0_152'
	}
	options {
		disableConcurrentBuilds()
		skipDefaultCheckout()
		timeout(time: 1, unit: 'HOURS')
		timestamps()
	}
	stages {
		stage('初始化流水线'){
			input {
				message "请选择需要发布的版本："
				parameters {
					choice(
						name: 'appVersion',
						choices : "${appVersions}"
					)
				}
			}
			steps {
				echo '开始初始化流水线!'
				println appVersion
				script {
					checkoutCode(basicGitUrl + devOpsProGitUrl ,devOpsProCertId, "chanpay-devops-qk-pipeline", "master")
					appInfoList = readJSON file:'chanpay-devops-qk-pipeline/chanpay-devops-qk-pipeline/test/config/app_info.json'
					serverInfoList = readJSON file:'chanpay-devops-qk-pipeline/chanpay-devops-qk-pipeline/test/config/server_info.json'
					selectAppTar = sh (script: "cd /data/online_tar/qk/${appVersion} && ls *.tar -1t", returnStdout: true).trim()
					selectAppVersion = appVersion
					println "selectAppTar:" + selectAppTar
					println "selectAppVersion:" + selectAppVersion
				}
				echo '完成初始化流水线!'
			}	
		}
		stage("解压程序包") {
			steps {
				echo "开始解压${selectAppTar}!"
				//解压程序包
				script {
					sh """
						cd /data/online_tar/qk/${selectAppVersion}
						tar xvf ${selectAppTar}
					"""
					needPublishZoneStr = sh (script: "cd /data/online_tar/qk/${selectAppVersion}/${selectAppVersion}/ && ls -1t", returnStdout: true)
					needPublishZoneList = needPublishZoneStr.split("\n")
				}
				echo "完成解压${selectAppTar}!"
			}
		}
		
		stage("传输程序包") {
			steps {
				echo "开始传输程序包!"
				sh """
					mkdir -p ${WORKSPACE}/qk_publish_tar && rm -rf ${WORKSPACE}/qk_publish_tar && mkdir -p ${WORKSPACE}/qk_publish_tar
					cd /data/online_tar/qk/${selectAppVersion}/${selectAppVersion}/ && cp -R * ${WORKSPACE}/qk_publish_tar
				"""
				script {
					for(needPublishZone in needPublishZoneList) {
						echo "传输${needPublishZone}程序"
						needPublishZoneServerList = serverInfoList[needPublishZone]
						for(needPublishZoneServer in needPublishZoneServerList){
							echo "传输程序到${needPublishZoneServer}服务器"
							sendFileToServer("${needPublishZoneServer}","qk_publish_tar/${needPublishZone}/","/qk_publish_tar/${selectAppVersion}","qk_publish_tar/${needPublishZone}/","")
						}
					}
				}
				echo "完成传输程序包!"
			}
		}
		
		stage("发布程序") {
			steps {
				echo "开始发布程序!"
				script {
					needPublishAllAppInfoList = []
					replica = serverInfoList["replica"]
					int currentReplica = 0
					while(currentReplica < replica) {
						for(needPublishZone in needPublishZoneList) {
							echo "发布${needPublishZone}程序"
							needPublishZoneServerList = serverInfoList[needPublishZone]
							//当前副本数已经大于该区域服务器总数
							if(currentReplica + 1 > needPublishZoneServerList.size()) {
								continue
							}
							needPublishZoneServer = needPublishZoneServerList[currentReplica]
							echo "暂停${needPublishZoneServer}服务器应用程序"
							stopCommand = "";
							needPublishAppStr = sh (script: "cd /data/online_tar/qk/${selectAppVersion}/${selectAppVersion}/${needPublishZone} && ls -1t", returnStdout: true)
							needPublishAppList = needPublishAppStr.split("\n")
							needPublishAppInfoList = []
							for(needPubshApp in needPublishAppList) {
								needPublishAppInfoList.add(appInfoList[needPubshApp.substring(0,needPubshApp.indexOf("${selectAppVersion}") - 1)])
							}
							
							for(needPublishAppInfo in needPublishAppInfoList) {
								if(needPublishAppInfo.singleNode == null || (needPublishAppInfo.singleNode != null && needPublishAppInfo.singleNode == needPublishZoneServer)) {
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
						
						for(needPublishZone in needPublishZoneList) {
							//停止服务
							echo "停止${needPublishZoneServer}服务器应用程序"
							needPublishZoneServerList = serverInfoList[needPublishZone]
							//当前副本数已经大于该区域服务器总数
							if(currentReplica + 1 > needPublishZoneServerList.size()) {
								continue
							}
							needPublishZoneServer = needPublishZoneServerList[currentReplica]
							shutdownCommand = "";
							needPublishAppStr = sh (script: "cd /data/online_tar/qk/${selectAppVersion}/${selectAppVersion}/${needPublishZone} && ls -1t", returnStdout: true)
							needPublishAppList = needPublishAppStr.split("\n")
							needPublishAppInfoList = []
							for(needPubshApp in needPublishAppList) {
								needPublishAppInfoList.add(appInfoList[needPubshApp.substring(0,needPubshApp.indexOf("${selectAppVersion}") - 1)])
							}
							
							for(needPublishAppInfo in needPublishAppInfoList) {
								if(needPublishAppInfo.singleNode == null || (needPublishAppInfo.singleNode != null && needPublishAppInfo.singleNode == needPublishZoneServer)) {
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
						
						
						for(needPublishZone in needPublishZoneList) {
							//备份服务
							echo "备份${needPublishZoneServer}服务器应用程序"
							needPublishZoneServerList = serverInfoList[needPublishZone]
							//当前副本数已经大于该区域服务器总数
							if(currentReplica + 1 > needPublishZoneServerList.size()) {
								continue
							}
							needPublishZoneServer = needPublishZoneServerList[currentReplica]
							backupCommand = "";
							needPublishAppStr = sh (script: "cd /data/online_tar/qk/${selectAppVersion}/${selectAppVersion}/${needPublishZone} && ls -1t", returnStdout: true)
							needPublishAppList = needPublishAppStr.split("\n")
							needPublishAppInfoList = []
							for(needPubshApp in needPublishAppList) {
								needPublishAppInfoList.add(appInfoList[needPubshApp.substring(0,needPubshApp.indexOf("${selectAppVersion}") - 1)])
							}
							
							for(needPublishAppInfo in needPublishAppInfoList) {
								if(needPublishAppInfo.singleNode == null || (needPublishAppInfo.singleNode != null && needPublishAppInfo.singleNode == needPublishZoneServer)) {
									backupCommand += "mkdir -p /opt/qk_app/${needPublishAppInfo.name}/lib/ \n"
									backupCommand += "touch /opt/qk_app/${needPublishAppInfo.name}/startup.sh \n"
									backupCommand += "touch /opt/qk_app/${needPublishAppInfo.name}/lib/chanpay-temp \n"
									backupCommand += "mkdir -p /data/qk_backup_jar/${selectAppVersion}/${needPublishAppInfo.name}/lib \n"
									backupCommand += "cp /opt/qk_app/${needPublishAppInfo.name}/lib/* /data/qk_backup_jar/${selectAppVersion}/${needPublishAppInfo.name}/lib \n"
									backupCommand += "cp /opt/qk_app/${needPublishAppInfo.name}/* /data/qk_backup_jar/${selectAppVersion}/${needPublishAppInfo.name}/ \n"
								}							
							}
							echo "备份程序：${needPublishZone}/${needPublishZoneServer}\n${needPublishAppList}"
							backupCommand += "pwd"
							sendFileToServer("${needPublishZoneServer}",
								"",
								"",
								"",
								"${backupCommand}")
						}
						sleep backupSleepSeconds
						
						
						for(needPublishZone in needPublishZoneList) {
							//解压服务
							echo "解压${needPublishZoneServer}服务器应用程序"
							needPublishZoneServerList = serverInfoList[needPublishZone]
							//当前副本数已经大于该区域服务器总数
							if(currentReplica + 1 > needPublishZoneServerList.size()) {
								continue
							}
							needPublishZoneServer = needPublishZoneServerList[currentReplica]
							unzipCommand = "";
							needPublishAppStr = sh (script: "cd /data/online_tar/qk/${selectAppVersion}/${selectAppVersion}/${needPublishZone} && ls -1t", returnStdout: true)
							needPublishAppList = needPublishAppStr.split("\n")
							needPublishAppInfoList = []
							for(needPubshApp in needPublishAppList) {
								needPublishAppInfo = appInfoList[needPubshApp.substring(0,needPubshApp.indexOf("${selectAppVersion}") - 1)]
								if(needPublishAppInfo.singleNode == null || (needPublishAppInfo.singleNode != null && needPublishAppInfo.singleNode == needPublishZoneServer)) {
									needPublishAppName = needPubshApp.substring(0,needPubshApp.indexOf("${selectAppVersion}") - 1)
									unzipCommand += "rm -f /opt/qk_app/${needPublishAppName}/*.jar && rm -f /opt/qk_app/${needPublishAppName}/lib/*.jar \n"
									unzipCommand += "tar xvf /data/qk_publish_tar/${selectAppVersion}/${needPubshApp} -C /opt/qk_app/ \n"
									unzipCommand += "chmod +x /opt/qk_app/${needPublishAppName}/startup.sh \n"
								}
							}
							echo "解压程序：${needPublishZone}/${needPublishZoneServer}\n${needPublishAppList}"
							
							unzipCommand += "pwd"
							sendFileToServer("${needPublishZoneServer}",
								"",
								"",
								"",
								"${unzipCommand}")
							sleep unzipSleepSeconds
							
							//启动服务
							echo "启动${needPublishZoneServer}服务器应用程序"
							publishCommand = "";
							needPublishAppStr = sh (script: "cd /data/online_tar/qk/${selectAppVersion}/${selectAppVersion}/${needPublishZone} && ls -1t", returnStdout: true)
							needPublishAppList = needPublishAppStr.split("\n")
							needPublishAppInfoList = []
							for(needPubshApp in needPublishAppList) {
								needPublishAppInfo = appInfoList[needPubshApp.substring(0,needPubshApp.indexOf("${selectAppVersion}") - 1)]
								if(needPublishAppInfo.singleNode == null || (needPublishAppInfo.singleNode != null && needPublishAppInfo.singleNode == needPublishZoneServer)) {
									needPublishAppName = needPubshApp.substring(0,needPubshApp.indexOf("${selectAppVersion}") - 1)
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
				echo "完成发布程序!"
			}
		}
	}
}