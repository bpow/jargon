
# Project: Jargon-core API
#### Date:  
#### Release Version: 4.2.0.1-SNAPSHOT 
#### git tag: 
#### Developer: Mike Conway - DICE

## News

4.2.0 Compatability and maintenance
for milestone: https://github.com/DICE-UNC/jargon/milestone/19


=======

Please go to [[https://github.com/DICE-UNC/jargon]] for the latest news and info.

Jargon-core consists of the following libraries

* jargon-core - base libraries, implementation of the iRODS protocol
* jargon-data-utils - additional functionality for dealing with iRODS data, such as building trees, storing information in iRODS on behalf of applications, and doing diffs between local and iRODS
* jargon-user-tagging - code for using free tagging and other metadata metaphors on top of iRODS
* jargon-user-profile - allows management of user profile and related configuration data in a user home directory
* jargon-ticket - support for ticket processing
* jargon-httpstream - stream http content into iRODS via Jargon
* jargon-ruleservice - support for running and managing rules from interfaces
* jargon-pool - initial implementation of commons-pool caching of iRODS agent connections.  This is initially for WebDav, and will be utilized as an option in REST and cloud browser.  Consider this code experimental
 
## Requirements

* Jargon depends on Java 1.8+
* Jargon is built using Apache Maven2, see POM for dependencies
* Jargon supports iRODS 4.1.0 through 4.2.X

## Libraries

Jargon-core uses Maven for dependency management.  See the pom.xml file for references to various dependencies.

Note that the following bug and feature requests are logged in GForge with related commit information [[https://github.com/DICE-UNC/jargon/issues]]

## Changes

#### Failures against 4.1.9 with neg require on server executing file.deleteWithForceOption in unit tests. #216

Fixes to flush behavior (related to #224) remaining after a switch to the SSL negotiation communication regime, corrections to behavior of flush() in client status operation send/receive in recursive delete operations