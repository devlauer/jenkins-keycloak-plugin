package org.jenkinsci.plugins;

import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.model.CNode;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;

import static io.jenkins.plugins.casc.misc.Util.getJenkinsRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.junit.Assert.*;

public class KeycloakSecurityRealmTest {
    @Rule
    public JenkinsConfiguredWithCodeRule chain = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("casc.yaml")
    public void configure_keycloak() {
        final Jenkins jenkins = Jenkins.getInstance();
        final KeycloakSecurityRealm securityRealm = (KeycloakSecurityRealm) jenkins.getSecurityRealm();
        assertEquals("{\n" +
			"  \"realm\": \"master\",\n" +
			"  \"auth-server-url\": \"https://keycloak.example.com/auth/\",\n" +
			"  \"ssl-required\": \"external\",\n" +
			"  \"resource\": \"ci-example-com\",\n" +
			"  \"credentials\": {\n" +
			"    \"secret\": \"secret-secret-secret\"\n" +
			"  },\n" +
			"  \"confidential-port\": 0\n" +
			"}", securityRealm.getKeycloakJson());
    }

    @Test
    public void export_casc_keycloak() throws Exception {
        KeycloakSecurityRealm ksr = new KeycloakSecurityRealm();
        ksr.setKeycloakJson("{\"realm\": \"master\",\"auth-server-url\": \"https://keycloak.example.com/auth/\",\"ssl-required\": \"external\",\"resource\": \"ci-example-com\",\"credentials\": {\"secret\": \"secret-secret-secret\"},\"confidential-port\": 0}");
        Jenkins.getInstance().setSecurityRealm(ksr);

        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        CNode yourAttribute = getJenkinsRoot(context).get("securityRealm").asMapping().get("keycloak");

        String exported = toYamlString(yourAttribute);

        String expected = toStringFromYamlFile(this, "KeycloakYamlExport.yaml");

        assertEquals(expected, exported);
    }
}