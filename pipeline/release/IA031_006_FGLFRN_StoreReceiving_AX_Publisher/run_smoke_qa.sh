set -euxo pipefail

####################################################################################################################################
# The pipeline should fail if any command in this script fails.
# Otherwise, Jenkins may report build as success even if some command in this script fails
# However, by default, Jenkins only take into account the status of the very last command in this file.
# The above line "set -euxo pipefail" will do the following:
# set -e will fail the script once any single command fails
# set -u will fail the script once the script is referencing an unset variable
# set -x will print out (log) every command executed
# set -o pipefail will fail the script if any command in a unix pipe fails
# Besides the above, you will need to add your own logic to detect failure in control structures (e.g. if, for, while, until)
# With something like "run_your_command || exit 1", where run_your_command is the command to run in if, for, while, or until
####################################################################################################################################

#kinit -kt ~/svc.common.keytab svc.common@CORP.AD.CTC

