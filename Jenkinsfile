#!groovy

/****************************************************************************/
/* Jenkins Pipeline File                                                    */
/* Version: 2.0                                                             */
/* Author: Robert Liu, Created: 2018-05-22                                  */
/* Last Modified: 2018-05-22                                                */
/* Description: Jenkins Pipeline sample library template                    */
/* For any changes, edit the schedule, deployment, cleanup, and rollback    */
/* stages below.                                                            */
/****************************************************************************/

// Add DSS shared libraries
// ---------------------------------------------------------------
@Library('dss-pipeline-library')
@Library('ci-cd-configurations')

def conf = SharedConfiguration.get()

node(conf.jenkinsSlave2) {

    properties([disableConcurrentBuilds()])

    deleteDir()
    checkout scm
    bitbucketGitInfo()

    library(identifier: "${env.GIT_REPO}@${env.BRANCH_NAME}", retriever: legacySCM(scm))
    def libraryName = "${env.BRANCH_NAME}".replaceAll('/', '_')
    echo "Jenkins Library Expected Is vars/${libraryName}.groovy"

    try {
        "${libraryName}"()
    } catch (NoSuchMethodError err) {
        echo "\u27A1  Caught: ${err}"
        error("Check if vars/${libraryName}.groovy exists. It's need to run pipeline on your branch. "  
            + "If the file exists, then please refer to the error message in the console.")
    }
}
