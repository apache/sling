[<img src="http://sling.apache.org/res/logos/sling.png"/>](http://sling.apache.org)

 [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

# Apache Sling Aggregator

This module is part of the [Apache Sling](https://sling.apache.org) project.

It provides an XML file that lists all Sling modules, to allow for tools like `repo` to process multiple repositories at once.

The list of modules is in a self-explaining format and can also be used in your own scripts if preferred.

Note that there are related efforts at [SLING-7331](https://issues.apache.org/jira/browse/SLING-7331) and [SLING-7262](https://issues.apache.org/jira/browse/SLING-7262), we'll need to consolidate all this at some point.

## Modules

You can find a list of the Apache Sling modules [here](docs/modules.md). 
This list is generated from the script [generate-aggregator-table.sh](https://github.com/apache/sling-whiteboard/blob/master/gh-badge-script/generate-aggregator-table.sh)..

### Updating Module Badges

We have a simple script to update the badges in GitHub's README.md files. To update all repositories:

    ./add-badges.sh [SLING_DIR]

To update a single repository:

    ./add-badges.sh [SLING_DIR] [REPO_NAME]

### Updating the Aggregator List

To update the aggregator list:

    ./generate-aggregator-table.sh [SLING_DIR]

### Dependencies

This script depends on the following utilities:

 - xpath
 - [grip](https://github.com/joeyespo/grip)

### Prerequisites

 1. Use the repo tool to extract all of the repositories in the [sling aggregator](https://github.com/apache/sling-aggregator)
 2. Ensure you have SSH based access enabled to GitHub
 3. Ensure all repository workspaces are in a clean state

## Retrieving all Sling modules

This module allows quick checkout of all Sling modules from Git. It requires
the local installation of the [repo](https://android.googlesource.com/tools/repo) tool.

### Repo Tool Installation (all platforms)

```
$ curl https://storage.googleapis.com/git-repo-downloads/repo > ~/bin/repo
$ chmod a+x ~/bin/repo
```

See also the detailed instructions at https://source.android.com/source/downloading#installing-repo.

### Repo Tool Installation on Mac with 2[Homebrew](https://brew.sh)

    brew install repo

### Synchronizing all Git repositories

Clone this repository if needed

```
git clone https://github.com/apache/sling-aggregator.git
cd sling-aggregator
```

Initialise the local repo checkout and synchronise all git repositories. The commands below must be run in the sling-aggreator git checkout.

```
$ repo init --no-clone-bundle -u https://github.com/apache/sling-aggregator.git
$ repo sync --no-clone-bundle
```

The output is a flat list of all Sling modules.

### Speeding up sync

Syncing all Sling modules can take a while, so if your network is fast enough you can try using multiple jobs in parallel. To use 16 jobs, run

```
$ repo sync -j 16
```

### Updating the list of modules

That list is found in the [default.xml](./default.xml) file.

It is used to generate the [list of Git Repositories](http://sling.apache.org/repolist.html) on our website.

**Install Groovy on Mac with [Homebrew](https://brew.sh)**

    brew install groovy

To update it:

    groovy collect-sling-repos.groovy > default.xml

Check changes with `git diff` and commit if needed.
