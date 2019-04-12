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
							execTimeout: 7200000,
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
		//buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '30' ))
	}
	parameters {
		choice(name: 'serverAddress', choices: '10.255.1.51\n10.255.0.113', description: '服务器（51测试/113联调）')
		string(name: 'parentBranch', defaultValue: 'master', description: '父工程分支')
		string(name: 'commonBranch', defaultValue: 'master', description: '公共工程分支')
		string(name: 'facadeBranch', defaultValue: 'master', description: '接口工程分支')
		string(name: 'projectBranch', defaultValue: 'master', description: '应用工程分支')
		
		booleanParam(name: 'is-parent-build-chanpay-parent', description: '父工程')
		booleanParam(name: 'is-parent-build-chanpay-parent-general', description: '通用父工程')
		booleanParam(name: 'is-parent-build-chanpay-common', description: '公共工程')
		booleanParam(name: 'is-parent-build-chanpay-facade', defaultValue: true, description: '接口工程')
		booleanParam(name: 'is-parent-build-chanpay-facade-client', defaultValue: true, description: '接口调用工程')
		
		booleanParam(name: 'is-build-chanpay-api-app', description: '[App接口服务]')
		booleanParam(name: 'is-build-chanpay-web-agent', description: '[销售平台]')
		booleanParam(name: 'is-build-chanpay-web-boss', description: '[运营平台]')
		booleanParam(name: 'is-build-chanpay-web-org',  description: '[服务商平台]')
		booleanParam(name: 'is-build-chanpay-web-user',  description: '[商户平台]')
		booleanParam(name: 'is-build-chanpay-gateway-platform-posp',  description: '[POSP对接服务]')
		booleanParam(name: 'is-build-chanpay-service-third-party',  description: '[三方渠道服务]')
		
		booleanParam(name: 'is-build-chanpay-api-platform',  description: '[账户API接口服务]')
		booleanParam(name: 'is-build-chanpay-wallet-api-platform',  description: '[钱包API接口服务]')
		booleanParam(name: 'is-build-chanpay-api-third-platform',  description: '[机构开放平台API接口服务]')
		booleanParam(name: 'is-build-chanpay-api-channel',  description: '[渠道API接口服务]')
		booleanParam(name: 'is-build-chanpay-api-third-merchant',  description: '[商户开放平台API接口服务]')
		
		booleanParam(name: 'is-build-chanpay-service-account',  description: '[账户服务]')
		booleanParam(name: 'is-build-chanpay-wallet-service-account',  description: '[账户钱包服务]')
		booleanParam(name: 'is-build-chanpay-service-wallet-flow',  description: '[账户钱包流水服务]')
		booleanParam(name: 'is-build-chanpay-service-user',  description: '[用户服务]')
		booleanParam(name: 'is-build-chanpay-service-trans',  description: '[交易服务]')
		booleanParam(name: 'is-build-chanpay-service-channel',  description: '[渠道服务]')
		
		booleanParam(name: 'is-build-chanpay-gateway-message',  description: '[消息网关]')
		booleanParam(name: 'is-build-chanpay-gateway-quickpay-notify',  description: '[快捷通知服务]')
		booleanParam(name: 'is-build-chanpay-gateway-linkface',  description: '[Linkface网关]')
				
		booleanParam(name: 'is-build-chanpay-gateway-unionpay-headquarters',  description: '[总银联代付]')
		booleanParam(name: 'is-build-chanpay-gateway-unionpay-tianjin-allchannel',  description: '[天津银联全渠道兴业]')
		booleanParam(name: 'is-build-chanpay-gateway-unionpay-tianjin-allchannel-cbhb',  description: '[天津银联全渠道渤海]')
		booleanParam(name: 'is-build-chanpay-gateway-unionpay-org-quickpay',  description: '[新无卡网关]')

		booleanParam(name: 'is-build-chanpay-gateway-authentication',  description: '[鉴权网关]')
		booleanParam(name: 'is-build-chanpay-service-base',  description: '[基础服务]')
		booleanParam(name: 'is-build-chanpay-service-message-receiver',  description: '[消息接收服务]')
		booleanParam(name: 'is-build-chanpay-service-message-trans-receiver',  description: '[交易消息接收服务]')
		booleanParam(name: 'is-build-chanpay-service-monitor',  description: '[监控服务]')
		booleanParam(name: 'is-build-chanpay-task-monitor',  description: '[监控任务]')
		booleanParam(name: 'is-build-chanpay-gateway-hsm-keyou',  description: '[加密网关]')
		booleanParam(name: 'is-build-chanpay-gateway-unionpay-quickpay',  description: '[银联快捷网关]')
		booleanParam(name: 'is-build-chanpay-service-app',  description: '[APP服务]')
		booleanParam(name: 'is-build-chanpay-service-message',  description: '[消息服务]')
		booleanParam(name: 'is-build-chanpay-service-message-sender',  description: '[消息发送服务]')
		booleanParam(name: 'is-build-chanpay-service-message-trans-sender',  description: '[交易消息发送服务]')
		booleanParam(name: 'is-build-chanpay-service-permission',  description: '[权限服务]')
		booleanParam(name: 'is-build-chanpay-task',  description: '[任务]')
		booleanParam(name: 'is-build-chanpay-service-cache',  description: '[缓存服务]')
		booleanParam(name: 'is-build-chanpay-gateway-sms',  description: '[短信网关]')
		booleanParam(name: 'is-build-chanpay-service-monitor-db',  description: '[数据库监控服务]')
		booleanParam(name: 'is-build-chanpay-service-risk',  description: '[风控服务]')
		booleanParam(name: 'is-build-chanpay-task-channel',  description: '[渠道任务]')
		booleanParam(name: 'is-build-chanpay-gateway-channel-notify',  description: '[渠道通知]')
		booleanParam(name: 'is-build-chanpay-gateway-chanjet-quickpay',  description: '[畅捷快捷网关]')
		booleanParam(name: 'is-build-chanpay-gateway-unionpay-new-quickpay',  description: '[银联新快捷网关]')
		booleanParam(name: 'is-build-chanpay-gateway-unionpay-qrcode',  description: '[银联二维码网关]')
		booleanParam(name: 'is-build-chanpay-gateway-chanjet-riskdecision',  description: '[畅捷风控网关]')
		booleanParam(name: 'is-build-chanpay-gateway-unionpay-posp-quickpay',  description: '[POSP快捷网关]')
		booleanParam(name: 'is-build-chanpay-gateway-new-channel',  description: '[新渠道网关]')
		booleanParam(name: 'is-build-chanpay-service-message-remit-receiver',  description: '[清算消息接收服务]')
		booleanParam(name: 'is-build-chanpay-service-message-remit-sender',  description: '[清算消息发送服务]')
		booleanParam(name: 'is-build-chanpay-service-file', description: '[文件服务]')
		booleanParam(name: 'is-build-chanpay-gateway-allhopes', description: '[指纹网关]')
	}
	stages {
		stage('初始化流水线'){
			steps {
				echo '开始初始化流水线!'
				script {
					checkoutCode(basicGitUrl + devOpsProGitUrl ,devOpsProCertId, "chanpay-devops-qk-pipeline", params.parentBranch)
					appInfoList = readJSON file:'chanpay-devops-qk-pipeline/chanpay-devops-qk-pipeline/test/config/app_info.json'
					serverInfoList = readJSON file:'chanpay-devops-qk-pipeline/chanpay-devops-qk-pipeline/test/config/server_info.json'
					println 'need build project :' + needBuildProList
					println 'app_info :' + appInfoList
					println 'server_info :' + serverInfoList
					
					for(param in params) {
						if(param.key.indexOf('is-build-') != -1 && param.value == true) {
							appInfo = appInfoList[param.key.substring(9)]
							if(appInfo == null || appInfo.zone == null) {
								continue
							}
							if(appInfo.zone == "ap_general")
								needBuildApGeneralProList.add(param.key.substring(9))
							else if(appInfo.zone == "ap_core")
								needBuildApCoreProList.add(param.key.substring(9))
							else if(appInfo.zone == "ap_middleware")
								needBuildApMiddlewareProList.add(param.key.substring(9))
							else if(appInfo.zone == "dmz_out")
								needBuildDmzOutProList.add(param.key.substring(9))
							else if(appInfo.zone == "dmz_pri")
								needBuildDmzPriProList.add(param.key.substring(9))
							else if(appInfo.zone == "dmz_general")
								needBuildDmzGeneralProList.add(param.key.substring(9))
							else if(appInfo.zone == "dmz_core")
								needBuildDmzCoreProList.add(param.key.substring(9))
						}
					}
					needBuildProList.add(needBuildApMiddlewareProList)
					needBuildProList.add(needBuildDmzOutProList)
					needBuildProList.add(needBuildDmzPriProList)
					needBuildProList.add(needBuildApGeneralProList)
					needBuildProList.add(needBuildApCoreProList)
					needBuildProList.add(needBuildDmzGeneralProList)
					needBuildProList.add(needBuildDmzCoreProList)
				}
				echo '完成初始化流水线!'
			}	
		}
		stage('检出构建依赖工程') {
			steps {
				echo '开始检出构建依赖工程!'
				//构建父工程
				script {
					if ( params['is-parent-build-chanpay-parent'] == true){
						echo '开始检出构建父工程!'
						appInfo = appInfoList[parentProName]
						checkoutCode("${basicGitUrl}" + appInfo.url,appInfo.certId,parentProName,params.parentBranch)
						sh "cd ${WORKSPACE}/${parentProName}/${parentProName} && ${buildCommand}"
					}
					if ( params['is-parent-build-chanpay-parent-general'] == true){
						echo '开始检出构建父工程!'
						appInfo = appInfoList[parentGeneralProName]
						checkoutCode("${basicGitUrl}" + appInfo.url,appInfo.certId,parentProName,params.parentBranch)
						sh 'cd ${WORKSPACE}/chanpay-parent/${parentGeneralProName} && ${buildCommand}'
					}
					if ( params['is-parent-build-chanpay-common'] == true){
						echo '开始检出构建公共工程!'
						appInfo = appInfoList[commonProName]
						checkoutCode("${basicGitUrl}" + appInfo.url,appInfo.certId,commonProName,params.commonBranch)
						sh 'cd ${WORKSPACE}/${commonProName}/${commonProName} && ${buildCommand}'
					}
					if ( params['is-parent-build-chanpay-facade'] == true){
						echo '开始检出构建接口工程!'
						appInfo = appInfoList[facadeProName]
						checkoutCode("${basicGitUrl}" + appInfo.url,appInfo.certId,facadeProName,params.facadeBranch)
						sh 'cd ${WORKSPACE}/${facadeProName}/${facadeProName} && ${buildCommand}'
					}
					if ( params['is-parent-build-chanpay-facade'] == true){
						echo '开始检出构建接口工程!'
						appInfo = appInfoList[facadeClientProName]
						checkoutCode("${basicGitUrl}" + appInfo.url,appInfo.certId,facadeProName,params.facadeBranch)
						sh 'cd ${WORKSPACE}/${facadeProName}/${facadeClientProName} && ${buildCommand}'
					}
				}
				echo '完成检出构建依赖工程!'
			}
		}
		
		stage('检出并构建项目') {
			steps {
				echo "开始检出并构建项目! "
				script {
					for(needBuildZoneProList in needBuildProList) {
						for(nbp in needBuildZoneProList) {
							appInfo = appInfoList["${nbp}"]
							echo "正在检出项目：${nbp}"
							checkoutCode("${basicGitUrl}" + appInfo.url, appInfo.certId, nbp, params.projectBranch)
							echo "正在构建项目：${nbp}"
							sh """
								cd ${WORKSPACE}/${nbp}/${nbp} && ${buildCommand}
								mkdir -p /data/base_data/temp_jar/qk/${BUILD_ID}/${nbp}/lib
								mkdir -p /data/base_data/temp_jar/qk/${BUILD_ID}/${nbp}/logs
								cd ${WORKSPACE}/${nbp}/${nbp}/target
								cp ${nbp}.jar /data/base_data/temp_jar/qk/${BUILD_ID}/${nbp}/${nbp}-${BUILD_ID}-${}.jar
								mkdir -p ${WORKSPACE}/${nbp}/${nbp}/target/lib
								cp -R lib/ /data/base_data/temp_jar/qk/${BUILD_ID}/${nbp}/
							"""
							echo "完成构建项目：${nbp}"
						}
					}
				}
				echo "完成检出并构建项目! "
			}
		}
		
		
		stage('检查代码风格') {
			steps {
				echo '开始检查代码风格!'
						
				echo '完成检查代码风格!'
			}
		}
		stage('检查代码质量') {
			steps {
				echo '开始检查代码质量!'
				
				echo '完成检查代码质量!'
			}
		}
		stage('检查代码安全') {
			steps {
				echo '开始检查代码安全!'
				
				echo '完成检查代码安全!'
			}
		}
		
		stage('单元测试') {
			steps {
				echo '开始单元测试!'
				
				echo '完成单元测试!'
			}
		}
		stage('API测试') {
			steps {
				echo '开始API测试!'
				
				echo '完成API测试!'
			}
		}
		stage('UI测试') {
			steps {
				echo '开始UI测试!'
				
				echo '完成UI测试!'
			}
		}
		stage('e2e集成测试') {
			steps {
				echo '开始e2e集成测试!'
				
				echo '完成e2e集成测试!'
			}
		}
		stage('发布到测试环境') {
			steps {
				echo '开始发布到测试环境!'
				
				echo '完成发布到测试环境!'
			}
		}
		stage('确认打包') {
			input {
				message "确认构建版本号${BUILD_ID}已通过测试，进行打包发布？请输入应用版本号："
				parameters {
					text(
						name: 'appVersion'
					)
				}
			}
			steps {
				echo '开始打包!'
				
				script {
					globalAppVersion = "${appVersion}"
					for(needBuildZoneProList in needBuildProList) {
						for(nbp in needBuildZoneProList) {
							sh (script: "cd ${WORKSPACE}/${nbp}/ && git rev-parse HEAD > gitVersion", returnStatus: false)
							gitVersion = readFile("${nbp}/gitVersion").trim()[0..8]
							sh (script: "ls", returnStdout: true)
							
							appInfo = appInfoList[nbp]
							currDate=new Date().format('yyyyMMdd');
							sh """
								mkdir -p /data/base_data/release_jar/qk/${appVersion}/${appInfo.zone}/
								cd ${WORKSPACE}/chanpay-devops-qk-pipeline/chanpay-devops-qk-pipeline/test/shell/ 
								cp startup.sh /data/base_data/temp_jar/qk/${BUILD_ID}/${nbp}/
								cd /data/base_data/temp_jar/qk/${BUILD_ID}/${nbp}/ 
								sed -i 's/{javaHome}/\\/usr\\/java\\/jdk-10/g' startup.sh
								sed -i 's/{serverDir}/\\/opt\\/qk_app\\/${nbp}/g' startup.sh
								sed -i 's/{jarName}/${nbp}-${appVersion}-${currDate}-${gitVersion}\\.jar/g' startup.sh
								sed -i 's/{pid}/${nbp}/g' startup.sh
								sed -i 's/{minMem}/${appInfo.minMem}/g' startup.sh
								sed -i 's/{maxMem}/${appInfo.maxMem}/g' startup.sh
								sed -i 's/{runtime}/online/g' startup.sh
								mv ${nbp}-${BUILD_ID}-${}.jar ${nbp}-${appVersion}-`date +%Y%m%d`-${gitVersion}.jar
								
								tar cf ${nbp}-${BUILD_ID}.tar ../${nbp}
								cp ${nbp}-${BUILD_ID}.tar /data/base_data/release_jar/qk/${appVersion}/${appInfo.zone}/${nbp}-${appVersion}-`date +%Y%m%d`-${gitVersion}.tar
							"""
							sh (script: "")
							
						}
					}
				  
				}
				//切换到工作空间，删除掉历史tar包
				//进行程序打包，放入制品库，同时将生产包放入工作空间，以便后续传输处理
				sh """
					cd ${WORKSPACE}/
					rm -f *.tar
					cd /data/base_data/release_jar/qk/${appVersion}/
					tar cf qk_release_${appVersion}_`date +%Y%m%d`.tar ../${appVersion}
					cp qk_release_${appVersion}_`date +%Y%m%d`.tar ${WORKSPACE}/
				"""
				echo '完成打包!'
			}
		}
		stage('传输到生产文件服务器') {
			steps {
				echo '开始传输到生产文件服务器!'
				echo "${globalAppVersion}"
				script {
					def nowDate = new Date().format('yyyyMMdd')
					tarName = "qk_release_${globalAppVersion}_${nowDate}.tar"
					sendFileToServer("${params.serverAddress}","${tarName}","/online_tar/qk/${globalAppVersion}","","")
				}
				echo '完成传输到生产文件服务器!'
			}
		}
	}
}