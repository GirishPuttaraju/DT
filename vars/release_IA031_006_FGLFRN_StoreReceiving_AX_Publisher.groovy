/** *************************************************************************/
/* Jenkins Pipeline File                                                    */
/* Version: 1.1                                                             */
/* Author: Jeff Mongeon, Created: 2018-08-20                                */
/* Last Modified: 2018-08-23                                                */
/* Description: Jenkins Pipeline sample library template                    */
/* For any changes, edit the schedule, deployment, cleanup, and rollback    */
/* stages below.                                                            */
/** *************************************************************************/

// DEVELOPER NOTE: EDIT JOB SCHEDULE AS REQUIRED
// Example here runs Jenkins job every 6 hours (uncomment to use):
// properties([pipelineTriggers([cron('0 H/6 * * *')])])
// ---------------------------------------------------------------
def call() {

    def conf = SharedConfiguration.get()

    // ****** DEVELOPER NOTE: Settings for Branch Here - Please update ******

    // HipChat notification room name - do not leave blank or comment out:
    env.TEAMS_HOOK = "CI-CD-FGLFRN_StoreReceiving_AX_Publisher"
   //env.HIPCHAT_ROOM = "CI-CD-FGLFRN_StoreReceiving_AX_Publisher"

    // set the env properties (conf is inherted as the defaults, but you can override as necessary)
    def targetGroup = conf.targetGroup_QA         // Branch type: conf.bwTargetGroup = dev, conf.bwTargetGroup_QA = qa
    def targetHost = conf.targetHost_QA           // Host to deploy to: conf.bwTargetHost = BW Dev, conf.bwTargetHost_QA = BW QA
    def targetHostUser = "svc.cicdbigdata"                         // SSH user: please add your service user

    // Zepyhr enterprise settings
    def zephyrProjectId = 149                     // Project ID in Zephyr - change for your project
  
    // pom.xml file path name - replace this:
    def pomFilePathName = "./IA031_006/BW/IA031_006_FGLFRN_StoreReceiving_AX_Publisher.application.parent/pom.xml"  //location of the parent pom.xml file
    def mvnGoals = "clean install"               // maven goals
    def profile = "default.substvar"             // Profile to use in env

    // ****** END BRANCH SETTINGS ******

    // Initialization, please do not change
    def sshKeyId = ""
    def branchName = env.GIT_BRANCH.replaceAll('release/', '')

    try {
        // ****** STAGES *******

        stage('Initialize pipeline') {
            pipelineConfig.initialize()
            // Set the SSH Key ID based on the target user and host
            sshKeyId = pipelineConfig.getSSHKeyId(targetHostUser, targetHost)
        }

        stage('Checkout Profile Configuration') {
            deleteDir()
            checkout scm
            bitbucketInfo()
            sshagent([bitbucketInfo.credentialID]) {
                sh "git clone ssh://${bitbucketInfo.username}@bitbucket.corp.ad.ctc:22/dac/${conf.mvnProfileRepo}.git"
            }
            stash name:'qatvars', includes:"${conf.mvnProfileRepo}/*"

        }
      
        stage('Branch Management') {
            echo pipelineConfig.pad("Create release branch if not already there")
            createNewReleaseBranch()
        }
      
        stage('Create project archive') {
            echo pipelineConfig.pad("Start to create project archive")
            pipelineConfig.createProjectBundle("${WORKSPACE}")
        }

        stage('Upload project to Artifactory server') {
            echo pipelineConfig.pad("Start to upload project to Artifactory server")
            artifactoryTools.uploadProject(conf.artifactoryUrl, conf.artifactoryRepo, conf.artifactoryId)
        }

        stage('Download playbooks from Artifactory server') {
            echo pipelineConfig.pad("Start to download 'Ansible playbooks' from Artifactory server")
            artifactoryTools.downloadAnsible(conf.artifactoryUrl, conf.artifactoryRepo, conf.artifactoryId, conf.playbooksName, conf.playbooksRelease, conf.playbooksVersion)
        }

        stage('Extract Ansible archive') {
            echo pipelineConfig.pad("start to extract Ansible Archive")
            ansibleTools.extract(conf.playbooksName, conf.playbooksRelease, conf.playbooksVersion)
        }

        stage('Project deployment') {
            echo pipelineConfig.pad("Start project deployment")
            ansibleTools.runDeployProject(sshKeyId, conf.artifactoryUrl, conf.artifactoryRepo, conf.artifactoryId, env.GIT_REPO, env.PROJECT_ARCHIVE, targetGroup, targetHostUser)
        }

        stage('ATF deploy') {
            echo pipelineConfig.pad("Start to deploy AFT project")
            ansibleTools.runDeployATF(sshKeyId, conf.artifactoryUrl, conf.artifactoryRepo, conf.artifactoryId, conf.atfVersion, conf.atfRelease, env.GIT_REPO, targetGroup, targetHostUser)
        }
		
		stage('Init Keytab') {
               echo pipelineConfig.pad("Init Keytab")
               initKeytab(sshKeyId, targetHost, targetHostUser)
        }
      
        stage('Tibco EMS') {
            echo pipelineConfig.pad("Tibco EMS")
            cmd = "atf-cli ems-provision-resources --map pipeline/release/${branchName}/resource-setup_qa.yml --ems-alias ems_qa_ssl"
            atf.runCommand(sshKeyId, targetHost, targetHostUser, env.GIT_REPO, cmd)
        }

        /*uncomment if required
        stage('Tibco BW Put File') {
            echo pipelineConfig.pad("Tibco BW Put File")
            cmd = "atf-cli tibco-put-file --bw-alias bw_qa --input-file ${bw_file_path}"
            atf.runCommand(sshKeyId, targetHost_QA, targetHostUser_QA, env.GIT_REPO, cmd)
        }*/
      
        stage('Tibco BW Check') {
           echo pipelineConfig.pad('Check BW variables')
           cmd = "atf-cli tibco-bw-check --bw-alias bw_qa --input-file ${pomFilePathName.replace('.parent','')}"
           atf.runCommand(sshKeyId, targetHost, targetHostUser, env.GIT_REPO, cmd)
       }

        docker.image('artifactory.corp.ad.ctc:5000/datatech/immutable/maven-jenkins-agent').inside() {
            stage('Build and Deploy BW Artifacts') {
                echo pipelineConfig.pad("Checkout SCM")
                checkout scm
                unstash 'qatvars'
                sh "virtualenv -p python tibcoVenv; source ./tibcoVenv/bin/activate; pip install -r ${conf.mvnProfileRepo}/requirements.txt"
                def profileBaseDir = mvnProfile.truncatePathName(pomFilePathName, 1, 2, '/')
                for (file in findFiles(glob: "${profileBaseDir}/**/${profile}")) {
                    sh "source ./tibcoVenv/bin/activate; python ${conf.mvnProfileRepo}/substvar.py ${file.path} ${conf.mvnProfileRepo}/${conf.mvnQaVarsFile} qa"
                }
                echo pipelineConfig.pad("Run Maven")
                mvn.run(pomFilePathName, profile, mvnGoals, conf, conf.bwTargetHost_QA)
            }
        }

        /* uncomment to deploy in secondary node*/
        // docker.image('artifactory.corp.ad.ctc:5000/datatech/immutable/maven-jenkins-agent').inside() {
        //     stage('Build and Deploy BW Artifacts') {
        //         echo pipelineConfig.pad("Checkout SCM")
        //         checkout scm
        //         unstash 'qatvars'
        //         sh "virtualenv -p python tibcoVenv; source ./tibcoVenv/bin/activate; pip install -r ${conf.mvnProfileRepo}/requirements.txt"
        //         def profileBaseDir = mvnProfile.truncatePathName(pomFilePathName, 1, 2, '/')
        //         for (file in findFiles(glob: "${profileBaseDir}/*${profile}")) {
        //             sh "source ./tibcoVenv/bin/activate; python ${conf.mvnProfileRepo}/substvar.py ${file.path} ${conf.mvnProfileRepo}/${conf.mvnQaVarsFile} qa"
        //         }
        //         echo pipelineConfig.pad("Run Maven")
        //         mvn.run(pomFilePathName, profile, mvnGoals, conf, conf.bwTargetHost_QA)
        //     }
        // }

    } catch (err) {
        // Gather and set build status
        echo "\u27A1  Caught: ${err}"

        // Error out of the build
        error err.getMessage()

    } finally {
        stage('Project cleanup and rollback if failed') {
            echo pipelineConfig.pad("Start rollback if failed")
            // Script "rollback.sh" will only run in case if one of these ('Init Run', 'Smoke tests' and 'Zephyr Tests') fails
            rollbackIfFailed(sshKeyId, targetHost, targetHostUser, env.GIT_REPO, "source ./pipeline/release/${branchName}/rollback_qa.sh")

            /* Uncomment to activate resources cleanup
            echo pipelineConfig.pad("Resources cleanup")
            cmd = "atf-cli sys-provision-resources --map pipeline/release/${branchName}/resource-cleanup_qa.yml --hive-alias bw_qa"
            atf.runCommand(sshKeyId, targetHost, targetHostUser, env.GIT_REPO, cmd)
            */

            echo pipelineConfig.pad("Start project cleanup")
            ansibleTools.runProjectCleanup(sshKeyId, env.GIT_REPO, targetGroup, targetHostUser)
        }
    }
}
