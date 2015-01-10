# Keycloak OAuth Plugin

 ![Keycloak Jenkins](https://raw.githubusercontent.com/mnadeem/jenkins-keycloak-plugin/master/jk.png)

Read More about [this](https://wiki.jenkins-ci.org/display/JENKINS/keycloak-oauth-plugin) plugin 

This plugin incorporates SSO in [Jenkins](http://jenkins-ci.org) with [Keycloak](http://keycloak.jboss.org/)

Usage
---
For usage information please refer the offical [wiki page](https://wiki.jenkins-ci.org/display/JENKINS/keycloak-oauth-plugin)

Building From Source
---
:radio_button: Checkout or download the source code from the latest tag on [GitHub](https://github.com/mnadeem/jenkins-keycloak-plugin).

:radio_button: Execute `mvn clean verify` from your local source code folder (install [Maven](http://maven.apache.org) if not already done).

:radio_button: Find the `keycloak.hpi` file in the `target` subfolder.


Testing The Plugin
---
Execute the following

	mvn hpi:run -Djetty.port=8090 -Dhpi.prefix=/jenkins

For more details refer the [official site](https://wiki.jenkins-ci.org/display/JENKINS/Plugin+tutorial)

:+1: :copyright:

