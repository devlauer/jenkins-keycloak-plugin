#!/bin/bash
mkdir ./plugins
mv ./target/keycloak.hpi ./plugins
docker run --rm -v ${TRAVIS_BUILD_DIR}/update-center.key:/juseppe/cert/uc.key -v ${TRAVIS_BUILD_DIR}/update-center.crt:/juseppe/cert/uc.crt -v ${TRAVIS_BUILD_DIR}/plugins/:/juseppe/plugins/ -v ${TRAVIS_BUILD_DIR}/:/juseppe/json/ -e JUSEPPE_BASE_URI=https://dl.bintray.com/devlauer/update-site/ lanwen/juseppe generate
curl --request DELETE --user ${CI_DEPLOY_USERNAME}:${CI_DEPLOY_PASSWORD} "https://api.bintray.com/packages/devlauer/update-site/jenkins-plugins/versions/latestupdates"
curl --request POST --header "Content-Type: application/json" --data '{"name":"latestupdates"}' --user ${CI_DEPLOY_USERNAME}:${CI_DEPLOY_PASSWORD} "https://api.bintray.com/packages/devlauer/update-site/jenkins-plugins/versions"
curl --request PUT --upload-file ./plugins/keycloak.hpi --user ${CI_DEPLOY_USERNAME}:${CI_DEPLOY_PASSWORD} "https://api.bintray.com/content/devlauer/update-site/jenkins-plugins/latestupdates/plugins/keycloak.hpi;publish=1;override=1"
curl --request PUT --upload-file ./update-center.json --user ${CI_DEPLOY_USERNAME}:${CI_DEPLOY_PASSWORD} "https://api.bintray.com/content/devlauer/update-site/jenkins-plugins/latestupdates/update-center.json;publish=1;override=1" 