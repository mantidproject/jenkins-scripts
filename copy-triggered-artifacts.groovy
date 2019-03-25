// Copies artifacts from upstream builds to this workspace.
// It can be used in one of two modes:
//   - manually triggered
//   - triggered by an upstream build automatically.
// In manually triggered mode it is assumed that the job has a Boolean parameter
// whose name gives the name of the job whose artifacts should be copied over.
// In automatic mode the first upstream job tree is searched until the first
// job with clean in the name is encountered.

import hudson.plugins.copyartifact.SpecificBuildSelector
import hudson.plugins.copyartifact.CopyArtifact
import hudson.model.AbstractBuild
import hudson.Launcher
import hudson.model.BuildListener
import jenkins.model.Jenkins

// Return all of the root triggers for this job
def getAllRootTriggers(causes) {
  // Return the initial jobs in each pipeline that caused this build to start
  parents = getUpstreamProjectTriggers(causes)

  def allRoots = [];
  for (trigger in parents) {
    allRoots.add(getCleanBuild(trigger))
  }
  return allRoots;
}

// Finds any clean builds amongst the root triggers
def getCleanBuild(rootTrigger) {
  while(true) {
    grandParentTriggers = getUpstreamProjectTriggers(rootTrigger.getUpstreamCauses())
    if(grandParentTriggers.size() > 1) {
      throw new Exception("getRootTrigger requires a single trigger cause")
    }
    if(grandParentTriggers.size() == 0) {
      break
    } else {
       rootTrigger = grandParentTriggers[0]
       // stop at a clean build
       if(rootTrigger.getUpstreamProject().contains("clean")) {
          break
       }
    }
  }
  println("Found root trigger:  ${rootTrigger.getUpstreamProject()}/${rootTrigger.getUpstreamBuild()}")
  return rootTrigger
}

// Get the upstream trigger for a given job
def getUpstreamProjectTriggers(causes) {
    // Return the upstream job that triggered the given job
    def upstreamCauses = []
    for (cause in causes) {
        if (cause.class.toString().contains("UpstreamCause")) {
            upstreamCauses.add(cause)
        }
    }
    return upstreamCauses
}

// Copy all artefacts from all triggers
def copyAllTriggeredResults(triggers) {
    for (trigger in triggers) {
      copySingleTriggeredResults(trigger)
    }
 }

// Copy artefacts from an upstream trigger
def copySingleTriggeredResults(trigger) {
   copyArtefact(trigger.getUpstreamProject(), trigger.getUpstreamBuild());
 }

// Copy a single artefact from the given job and build ID
def copyArtefact(jobName, buildId) {
   println("Copy artifact from build:  ${jobName}/${buildId}")
   def copyArtifact = new CopyArtifact(jobName)
   copyArtifact.setSelector(new SpecificBuildSelector(String.valueOf(buildId)))
   copyArtifact.setFlatten(true)
   copyArtifact.setTarget("artifacts")

   // use reflection because direct call invokes deprecated method
   def perform = copyArtifact.class.getMethod("perform", AbstractBuild, Launcher, BuildListener)
   perform.invoke(copyArtifact, build, launcher, listener)
}

// Copy artefacts from manually selected builds
def copySelectedArtefacts(buildptr) {
    params = buildptr.getActions(hudson.model.ParametersAction)[0].getAllParameters()
    for(param in params) {
        if(param.getValue()) {
          def name = param.getName();
          def job = Jenkins.instance.getItemByFullName(param.getName())
          copyArtefact(name, job.getLastSuccessfulBuild().getNumber())
        }
    }
}


//-----------------------------------------------------------------------------
// Main
//-----------------------------------------------------------------------------

// Were we started directly or by another job? For user then assume build.getCauses is a single-valued array
causes = build.getCauses()
if (causes[0].class.toString().contains("UserIdCause")) {
  println("Manually triggered build. Copying selected artefacts")
  copySelectedArtefacts(build)
} else {
  println("Automatically triggered build. Copying artifacts from root clean build")
  // Find root trigger jobs (assuming it is clean build) and copy artefacts
  triggers = getAllRootTriggers(causes)
  copyAllTriggeredResults(triggers)
}
