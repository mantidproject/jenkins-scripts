#!/bin/bash
# Creates or updates a branch on Github to match a given ref.
GITHUB_API_URL=https://api.github.com

# arguments
if [ $# -ne 4 ]; then
  echo "Usage: create-or-update-branch.sh branchname repo ref credentials"
  echo
  echo "   branchname - Name of the branch to create/update"
  echo "   repo - Full name of repository to act on as owner/repo, e.g. mantidproject/mantid"
  echo "   ref - Reference that branch should be set to, e.g. master"
  echo "   credentials - Username and token with push access to repo in format username:token"
  exit 1
fi
branchname=${1}
repo=${2}
ref=${3}
credentials=${4}

# Get the base reference
repo_url=${GITHUB_API_URL}/repos/${repo}
base_sha=$(curl -s -X GET ${repo_url}/git/ref/heads/${ref} | jq --raw-output .object.sha)
if [ "${base_sha}" = "null" ]; then
   echo "Cannot find matching reference ${ref} in ${repo}"
   exit 1
else
    echo "Found base reference '${ref}' on '${repo}'"
fi

# Get the current branch ref if it exists
current_branch_ref=$(curl --request GET \
                          ${repo_url}/git/ref/heads/${branchname} | jq --raw-output .object.sha)
if [ "${current_branch_ref}" != "null" ]; then
    echo "${branchname} exists, updating reference to it with value ${ref}"
    response=$(curl --user ${credentials} \
                          --request POST \
                          --data "{\"sha\": \"${base_sha}\", \"force\": true}" \
                          ${repo_url}/git/refs/heads/${branchname})
    branchname_sha=$(echo ${repsonse} | jq --raw-output .object.sha)
    action=updated
else
    echo "${branchname} does not exist, creating reference to it with value ${ref}"
    # branch does not exist - create it
    response=$(curl --user ${credentials} \
                          --request POST \
                          --data "{\"ref\": \"refs/heads/${branchname}\", \"sha\": \"${base_sha}\"}" \
                          ${repo_url}/git/refs)
    branchname_sha=$(echo ${response} | jq --raw-output .object.sha)
    action=created
fi

if [ "${updated_sha}" != "null" ]; then
    echo "Successfully ${action} ${branchname} at ref ${branchname_sha}"
else
    echo "An error occurred. Run script with bash -ex to debug."
fi
