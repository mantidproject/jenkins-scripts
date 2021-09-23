#!/bin/bash

# Check if a given pull request is in a mergeable state
# See https://docs.github.com/en/rest/guides/getting-started-with-the-git-database-api#checking-mergeability-of-pull-requests
# for the background to the steps followed here.
# It is assumend an environment variable GITHUB_OAUTH_TOKEN is defined
# An exit status of:
#  - 0: pull request can be merged
#  - 1: pull request has conflicts
#  - 2: other error

EXIT_STATUS_MERGEABLE=0
EXIT_STATUS_ERRORS=1
EXIT_STATUS_CONFLICTS=2

function usage {
  echo 'Check if a given pull request is in a mergeable state'
  echo "Usage: $0 owner/repo pull_request_number"
  exit $EXIT_STATUS_ERRORS
}

if [[ $# -ne 2 ]]; then
  usage
fi

BASE_REPO=$1
PULL_NUMBER=$2
GH_TOKEN=${GITHUB_OAUTH_TOKEN:?Missing GITHUB_OAUTH_TOKEN environment variable}

# Use curl or wget?
HAVE_CURL=false
HAVE_WGET=false
if [ $(command -v curl) ]; then
  HAVE_CURL=true
elif [ $(command -v wget) ]; then
  HAVE_WGET=true
else
  echo "Unable to find curl or wget. Cannot continue"
  exit $EXIT_STATUS_ERRORS
fi

# GET json data using either curl or wget depending on what is available
function _get_json() {
  local endpoint=$1

  local json_encoding_header="Content-Type: application/json; charset=utf-8"
  if [[ ${HAVE_CURL} == true ]]; then
    curl \
      --silent \
      --header "$json_encoding_header" \
      --request GET \
      $endpoint
  else
    # assume script exits if wget/curl not available
    wget \
      --quiet \
      --output-document=- \
      --header "$json_encoding_header" \
      "$endpoint"
  fi
}

# POST json data using either curl or wget depending on what is available
function _post_json() {
  local endpoint="$1"
  local data="$2"

  local json_encoding_header="Content-Type: application/json; charset=utf-8"
  local authorization_header="Authorization: bearer ${GH_TOKEN}"
  if [[ ${HAVE_CURL} == true ]]; then
    curl \
      --silent \
      --header "$authorization_header" \
      --header "$json_encoding_header" \
      --request POST \
      "$endpoint" \
      --data "$data"
  else
    wget \
      --quiet \
      --output-document=- \
      --header "$authorization_header" \
      --header "$json_encoding_header" \
      --post-data="$data" \
      "$endpoint"
  fi
}

# Query a single pull request
function pull_request_info() {
  local repo=$1
  local pr_number=$2

  _get_json https://api.github.com/repos/${repo}/pulls/${pr_number}
}

function pull_request_mergeable() {
    local orgrepo=$1
    local pr_number=$2

    # Form graphql query on the mergeable status
    # as it allows for an unknown state while it is being calculated
    local owner=$(echo $orgrepo | cut -d'/' -f 1)
    local name=$(echo $orgrepo | cut -d'/' -f 2)
    local query=$(cat <<EOF
query {
  repository(owner:\"${owner}\", name: \"${name}\") {
    pullRequest(number: ${pr_number}) { mergeable }
  }
}
EOF
)
    # At time of writing newlines are not allowed in the query
    query="$(echo $query)"

    # Make request
    response=$(_post_json https://api.github.com/graphql "{\"query\": \"$query\"}")

    # Parse JSON response and extract mergable status.
    # Uses Python as jq is not available
    echo $(python3 -c "import json;print(json.loads('$response')['data']['repository']['pullRequest']['mergeable'])")
}

# Trigger an asynchronous calculation of whether the pull request is mergeable
# Deliberately ignoring output as it might be out of date
echo "Triggering calculation of mergability for pull request #${PULL_NUMBER}"
pull_request_info ${BASE_REPO} ${PULL_NUMBER} > /dev/null

# Check value of mergeable flag. See https://docs.github.com/en/graphql/reference/enums#mergeablestate
# for allowed states
counter=0
max_tries=5
while [ "$counter" -lt $max_tries ]; do
  mergeable="$(pull_request_mergeable ${BASE_REPO} ${PULL_NUMBER})"
  if [ "$mergeable" == "MERGEABLE" ]; then
    echo "Pull request can be merged."
    exit $EXIT_STATUS_MERGEABLE
  elif [ "$mergeable" == "CONFLICTING" ]; then
    echo
    echo "Pull request ${PULL_NUMBER} cannot be merged as there are conflicts. Please fix them by rebasing against the base branch."
    echo
    exit $EXIST_STATUS_CONFLICTS
  elif [ "$mergeable" == "UNKNOWN" ]; then
    echo "Mergeable status is still being computed."
    counter=$(( counter + 1 ))
    sleep 1
  else
    echo "Unknown state returned from pull request mergability check. Found '${mergable}', expected one of (MERGABLE|CONFLICTING|UNKNOWN)"
    echo "Perhaps the pull request has been closed?"
    exit $EXIT_STATUS_ERRORS
  fi
done

if [ "$counter" -eq $max_tries ]; then
  echo "Unable to determine mergability of pull request #${PULL_NUMBER}. Perhaps contact GitHub?"
  exit $EXIT_STATUS_ERRORS
fi
