// Look over the current build queue of pull request jobs
// and kill any duplicate builds leaving the latest intact.
// Two jobs are defined as duplicate if they are for the
// same OS and pull request number.

import hudson.model.*

PULL_REQUEST_JOB_NAME_PREFIX = 'pull_requests-'

def queue = Jenkins.instance.queue
def pr_queue = queue.items.findAll { it.task.name.startsWith(PULL_REQUEST_JOB_NAME_PREFIX) }

// map job names to queued items
def pr_job_map = [:]
pr_queue.each {
  jobs = pr_job_map.get(it.task.name, [])
  jobs << it
}

pr_job_map.each { name_jobs ->
  println name_jobs.getKey()
  jobs = name_jobs.getValue()
  pr_task_map = [:]
  jobs.each { job ->
    def params_list = job.params.split()
    pr_number = params_list[4]
    if (!pr_number.startsWith("PR=") ) {
      throw new RuntimeException('Unable to find PR= in job params list')
    }
    pr_tasks = pr_task_map.get(pr_number, [])
    pr_tasks << job
  }
  def jobs_killed = false
  pr_task_map.each { pr_task ->
    tasks = pr_task.getValue()
    if (tasks.size() > 1 ) {
      tasks.sort { it.id }
      tasks[0..-2].each { task ->
	    println "  Killing old queued job for " + pr_task.getKey() + " with ID=" + task.id
        queue.cancel(task)
        jobs_killed = true
      }
    }
  }
  if (!jobs_killed) {
      println "  No duplicate PR jobs found"
  }
}