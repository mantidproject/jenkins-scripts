/**
 * Clear the queue and cancel all running jobs.
 */

import hudson.model.Jenkins

// Stop all of the queued jobs.

def cancelledQueued = []

for (queuedJob in Jenkins.instance.queue.items) {
    cancelledQueued.add(queuedJob.getParams())
    Jenkins.instance.queue.cancel(queuedJob.task)
}


// Stop all of the currently running jobs.

def cancelledRunning = []

for (runningJob in Jenkins.instance.items) {
    stopJob(runningJob)
}

def stopJob(job) {
    if (job in com.cloudbees.hudson.plugins.folder.Folder) {
        for (child in job.items) {
            stopJob(child)
        }
    } else if (job in org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject) {
        for (child in job.items) {
            stopJob(child)
        }
    } else if (job in org.jenkinsci.plugins.workflow.job.WorkflowJob) {

        if (job.isBuilding()) {
            for (build in job.builds) {
                cancelledRunning.add(build.getDisplayName())
                build.doStop()
            }
        }
    }
}


// Output removed jobs to console so they can be restarted later.
println("\n\n")
println("Cancelled queued jobs:")
for (jobStr in cancelledQueued) {
    println(jobStr)
}
println()
println("Aborted running jobs:")
for (jobStr in cancelledRunning) {
    println(jobStr)
}