/**
 * Set value of Global property BRANCH_TO_PUBLISH that determines which of the
 * nightly pipelines will be published.
 *
 * Required variable bindings:
 *   branchName - Name of the branch to publish, usually either main or release-next
 */
def jenkins = jenkins.model.Jenkins.instance;
def nodes = jenkins.getGlobalNodeProperties()
nodes.getAll(hudson.slaves.EnvironmentVariablesNodeProperty.class)

if ( nodes.size() != 1 ) {
  println("error: unexpected number of environment variable containers: "
          + nodes.size()
          + " expected: 1")
} else {
  def envVars = nodes.get(0).getEnvVars()
  envVars.put("BRANCH_TO_PUBLISH", branchName)
  jenkins.save()
  println("BRANCH_TO_PUBLISH set to " + branchName)
}
