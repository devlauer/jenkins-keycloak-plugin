# Keycloak OAuth Plugin

 ![Keycloak Jenkins](https://raw.githubusercontent.com/mnadeem/jenkins-keycloak-plugin/master/jk.png)


This plugin incorporates SSO in [Jenkins](http://jenkins-ci.org) with [Keycloak](http://keycloak.jboss.org/)

Installation
---
As long as this plugin is not part of the official jenkins plugin repository, you have to install it through a separate [update-site](https://dl.bintray.com/devlauer/update-site/update-center.json). 
To make this site available in your jenkins instance, you have to edit the file hudson.model.UpdateCenter.xml in your configuration folder and paste the following code inside the sites node:

```xml
    <site>
      <id>elnarion-jenkins-site</id>
      <url>https://dl.bintray.com/devlauer/update-site/update-center.json</url>
      <caCertificate>-----BEGIN CERTIFICATE-----
MIIDcTCCAlmgAwIBAgIJAL6TanUM7BmsMA0GCSqGSIb3DQEBCwUAME8xCzAJBgNV
BAYTAkRFMRMwEQYDVQQIDApTb21lLVN0YXRlMSswKQYDVQQKDCJFbG5hcmlvbiB1
cGRhdGUgY2VudGVyIGZvciBqZW5raW5zMB4XDTE3MDMyNDExMzU1MFoXDTIwMDMy
MzExMzU1MFowTzELMAkGA1UEBhMCREUxEzARBgNVBAgMClNvbWUtU3RhdGUxKzAp
BgNVBAoMIkVsbmFyaW9uIHVwZGF0ZSBjZW50ZXIgZm9yIGplbmtpbnMwggEiMA0G
CSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC7JHkELmMt9xecMQkJshnlR92U6/mn
rnkLxfH75d47jGBpYrZpTtmyAwVaCDGKXko60F0WRYySp/wSL2rh4UN91M8SKB9Q
EgTPJJDc5zG4iwfunmD8fiHtw+ELcZepUt55jKqo1d4hhiaa7PwW7Ow2KAyrEVaV
6uQWUrRDK2yZ/kn7MDmrHk1ec3sXnZs56ljA1834fgi2aarJC3dD9wM809yAN/6e
Mhcfu8Q6Y81kKjIANMdAmO0/+fo1/8Sn22OAapjBPHxOeFdPhSlDrsgB3H5Yek7F
uszPP9S9TRta+ysWkEww0uAWXPYBL3ZaZdyPhDYN8QbuPEKl438uzJmNAgMBAAGj
UDBOMB0GA1UdDgQWBBRLLkO2CNufujS0xB3d+29qh+vvgTAfBgNVHSMEGDAWgBRL
LkO2CNufujS0xB3d+29qh+vvgTAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBCwUA
A4IBAQCiXopJLeIV3Etln4kb+PYXoqzTVAfEmGf+vPm3up9MYrkA0X9+eeGrUUEY
b7s7xL4VZXE4IJWB5gGSi1JZH6DnLmePRAOCupM2HV7KVm65SxAFV3hticRJYDzL
v8H4qEbzSGZQlbsAYIAZnO6dLsPO96eSdSSb73loPNl5UZjC1I74WGZkpD2xHqkX
S3iGpfC6dHgN68uaeEC1xQw1Rz7M7bInM/9lAvakegQBOEvCODk652Aq3SYSfJv2
pNVwmnvTHXnJM9/aL4iaihWyAMSC4MwfHYy2NhQVwRxerd0YfyC9OUJ1UPSOsYp8
L/0l3cdCRdmmphpJqme3g3k7tlgN
-----END CERTIFICATE-----</caCertificate>
      <disabled>false</disabled>
      <note></note>
    <site>
```

As an alternative to directly editing the configuration file, you could install/use the "Manage UpdateSites Plugin" with the same values as shown above.

Afterwards you have to restart jenkins, click on the "Check now"-Button under "Manage Jenkins > Manage Plugins", select the "Keycloak Authentication Plugin" and click on "install" and "restart jenkins".

Usage
---
For usage information please refer the offical [wiki page](https://wiki.jenkins-ci.org/display/JENKINS/keycloak-plugin)

Building From Source
---
:white_check_mark: Checkout or download the source code from the latest tag on [GitHub](https://github.com/mnadeem/jenkins-keycloak-plugin).

:white_check_mark: Execute `mvn clean verify` from your local source code folder (install [Maven](http://maven.apache.org) if not already done).

:white_check_mark: Find the `keycloak.hpi` file in the `target` subfolder.


Testing The Plugin
---
Execute the following

	mvn hpi:run -Djetty.port=8090 -Dhpi.prefix=/jenkins

For more details refer the [official site](https://wiki.jenkins-ci.org/display/JENKINS/Plugin+tutorial)

:+1: :octocat:

TODO
---

:pushpin: Authorization support

Nice images are from [Emoji](http://www.emoji-cheat-sheet.com/):copyright:

[![Bitdeli Badge](https://d2weczhvl823v0.cloudfront.net/mnadeem/jenkins-keycloak-plugin/trend.png)](https://bitdeli.com/free "Bitdeli Badge")
