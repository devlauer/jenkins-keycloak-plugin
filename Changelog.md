# Changelog

Only noting significant user changes, not internal code cleanups and minor bug fixes.

## [Unreleased]
- Added wiki documentation to project (Pull Request #6; thanks to halkeye)
- Several version upgrades (parent-pom, java version, jenkins minimum version, etc.) (Pull Request #7; thanks to halkeye)
- Updated Keycloak version to 19.0.2
- Added maintenance hint in README
- Updated jackson-core to 2.12.7
- Updated mailer to 391.ve4a_38c1b_cf4b_

## [2.3.0] - 2019-01-20
- [JENKINS-55669] Fix authentication loop  (Pull Request #5; thanks to Wadeck)
- Add support for pulling authorities from a "roles" claim (Pull Request #4; thanks to imduffy15)
- Updated Keycloak client version to 4.8.0.Final. 


## [2.2.0] - 2018-03-11
- Added IDP feature in Auth url (Pull Request #3; thanks to gigaga)


## [2.1.1] - 2018-02-19
- Fixed Plugin URL (Pull Request #1; thanks to rinrinne)
- Validating adapter config before saving (Pull Request #2; thanks to rinrinne)
- Updated Keycloak client version to 3.4.3.Final. Client is also compatible to 3.0.0 keycloak server.


## [2.1.0] - 2017-12-01
- Global sign out is now supported. Checks are done either on access token time out or on each user request (JENKINS-48158). To enable this feature please check new configuration elements in system configuration.
- Updated Keycloak client version to 3.4.0.Final. Client is also compatible to 3.0.0 keycloak server.

## [2.0.3] - 2017-07-16
###Changed
- Updated Keycloak client version to 3.2.0.Final. Client is also compatible to 3.0.0 keycloak server.
- Added openidconnect scope to the initial keycloak request because keycloak 3.2.0.Final server does not accept openidconnect requests without a proper scope anymore (KEYCLOAK-3316, KEYCLOAK-3237)

## [2.0.2] - 2017-04-02
###Changed
- Updated Keycloak client version to 3.0.0.Final. Client is also compatible to 2.5.x keycloak server.

## [2.0.1] - 2017-04-01
First release on jenkins-ci infrastructure. 
###Changed
- Supported Keycloak version is now 2.5.5.Final

## Older releases
Older releases can be found at a separate [update-site](https://dl.bintray.com/devlauer/update-site/update-center.json). 
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
