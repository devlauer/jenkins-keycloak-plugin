package org.jenkinsci.plugins;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.keycloak.adapters.KeycloakDeployment;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;

public class KeycloakUserDetailsService implements UserDetailsService {
	private static final Logger LOGGER = Logger.getLogger(KeycloakUserDetailsService.class.getName());

	private KeycloakSecurityRealm securityRealm;

	public KeycloakUserDetailsService(KeycloakSecurityRealm securityRealm) {
		this.securityRealm = securityRealm;
	}

	@Override
	public UserDetails loadUserByUsername( String username ) throws UsernameNotFoundException, DataAccessException {
		try {
			KeycloakDeployment keycloakDeployment = securityRealm.getKeycloakDeployment();
			KeycloakAccess keycloakAccess = new KeycloakAccess( keycloakDeployment );
			return keycloakAccess.loadUserByUsername( username );
		} catch ( IOException e ) {
			LOGGER.log( Level.INFO, "Unable to get user information from Keycloak for: " + username, e );
			throw new DataRetrievalFailureException( "Unable to get user information from Keycloak for " + username, e );
		}
	}
}
