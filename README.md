# Keycloak Plugin

---
**NOTE**

This plugin is not actively maintained anymore. 

---


<img src="https://jenkins.io/images/226px-Jenkins_logo.svg.png" alt="" width="100" /><img src="https://github.com/keycloak/keycloak/raw/master/themes/src/main/resources/theme/keycloak/welcome/resources/keycloak_logo.png" alt="" width="250" />


About
---
This plugin incorporates SSO in [Jenkins] with [Keycloak]


Installation
---
1. Within the Jenkins dashboard, click Manage Jenkins.
2. In the Manage Jenkins page, click Manage Plugins.
3. Click the Available tab.
4. Filter for keycloak
5. Click either “Install without restart” or “Download now and install after restart”.
6. Restart Jenkins if necessary.

Usage
---
For usage information please refer the offical [wiki]

License
---
Jenkins-Keycloak-Plugin is **licensed** under the **[MIT License]**.

Versioning
---
This plugin uses sematic versioning. For more information refer to [semver]

Changelog
---
This plugin has a dedicated [Changelog].

Reporting bugs and feature requests
---
We use the [Jenkins JIRA] to log all bugs and feature requests. Create a [new account], browse to [Jenkins JIRA] and login with your account then create a new issue with the component `keycloak-plugin`.

Source
---
Latest and greatest source of Jenkins Keycloak Plugin can be found on [GitHub]. Fork it!

Building From Source
---
* Checkout or download the source code from the latest tag on [GitHub]
* Execute `mvn clean verify` from your local source code folder (install [Maven] if not already done).
* Find the `keycloak.hpi` file in the `target` subfolder.

Testing The Plugin
---
Execute the following

	mvn hpi:run -Djetty.port=8090 -Dhpi.prefix=/jenkins

For more details refer the [official plugin tutorial](https://wiki.jenkins-ci.org/display/JENKINS/Plugin+tutorial)


Notice
---
This repository was forked from this  [repository](https://www.github.com/devlauer/jenkins-keycloak-plugin), which was originally a fork of this [repository](https://www.github.com/keycloak/jenkins-keycloak-plugin)

[semver]: http://semver.org
[Jenkins]: http://jenkins-ci.org 
[Keycloak]: http://keycloak.jboss.org/
[new account]: https://accounts.jenkins.io/
[Jenkins JIRA]: https://issues.jenkins-ci.org/
[official plugin tutorial]: https://wiki.jenkins-ci.org/display/JENKINS/Plugin+tutorial
[MIT License]: https://github.com/jenkinsci/keycloak-plugin/raw/master/LICENSE
[Changelog]: https://github.com/jenkinsci/keycloak-plugin/blob/master/Changelog.md
[GitHub]: https://github.com/jenkinsci/keycloak-plugin
[Maven]: http://maven.apache.org
[wiki]: https://wiki.jenkins-ci.org/display/JENKINS/keycloak-plugin