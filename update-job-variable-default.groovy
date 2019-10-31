/**
 * Update the default value of a job parameter on a list of jobs
 *
 * Required variable bindings:
 *   jobNames - A comma-separated list of jobname names
 *   parameterName - The name of the parameter to change
 *   parameterValue - The new value of the parameter.
 */
import hudson.model.*

// Retrieve matching jobs
def jenkins = jenkins.model.Jenkins.instance;
def chosenJobs = jenkins.items.findAll{job -> jobNames.contains(job.name)};

// Do update
chosenJobs.each { job ->
  paramsDef = job.getAction(ParametersDefinitionProperty)
  params = paramsDef.getParameterDefinitions()
  params.each { it ->
    if(it.getName() == parameterName) {
      println("Updating default value of ${parameterName} variable to ${parameterValue} on ${job.name} ")
      it.setDefaultValue(parameterValue)
      job.save()
    }
  }
}
