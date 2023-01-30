/**
 * Enable specified jobs.
 *
 * Required variable bindings:
 *   jobNames - A comma delimited list of projects to check
 */
def jenkins = jenkins.model.Jenkins.instance;
def chosenJobs = jenkins.items.findAll{job -> jobNames.contains(job.name)};

chosenJobs.each { job ->
  println "Enabling job " + job.name;
  job.doEnable()
}
