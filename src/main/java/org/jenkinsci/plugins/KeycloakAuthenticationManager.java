package org.jenkinsci.plugins;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.BadCredentialsException;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.rotation.AdapterTokenVerifier;
import org.keycloak.common.VerificationException;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.IDToken;

public class KeycloakAuthenticationManager implements AuthenticationManager {
	private static final Logger LOGGER = Logger.getLogger( KeycloakAuthenticationManager.class.getName() );

	private KeycloakSecurityRealm securityRealm;

	public KeycloakAuthenticationManager( KeycloakSecurityRealm securityRealm ) {
		this.securityRealm = securityRealm;
	}

	@Override
	public Authentication authenticate( Authentication authentication ) throws AuthenticationException {
		if ( authentication instanceof KeycloakAuthentication ) {
			return authentication;
		}

		//Do User/Pass auth
		if ( authentication.getPrincipal() != null && authentication.getCredentials() != null ) {
			LOGGER.fine( "Keycloak Authentication Manager authenticate: " + authentication.getPrincipal().toString() );
			String username = authentication.getPrincipal().toString();
			String password = authentication.getCredentials().toString();

			try {
				KeycloakDeployment keycloakDeployment = securityRealm.getKeycloakDeployment();

				KeycloakAccess keycloakAccess = new KeycloakAccess( keycloakDeployment );
				AccessTokenResponse tokenResponse = keycloakAccess.getAccessToken( username, password );

				String tokenString = tokenResponse.getToken();
				String idTokenString = tokenResponse.getIdToken();
				String refreshToken = tokenResponse.getRefreshToken();

				AccessToken token = AdapterTokenVerifier.verifyToken( tokenString, keycloakDeployment );
				if ( idTokenString != null ) {
					JWSInput input = new JWSInput( idTokenString );
					IDToken idToken = input.readJsonContent( IDToken.class );
					return new KeycloakAuthentication( idToken, token, refreshToken, tokenResponse );
				} else {
					return new KeycloakAuthentication( new IDToken() {
						@Override
						public String getPreferredUsername() {
							return username;
						}
					}, token, refreshToken, tokenResponse );
				}
			} catch ( IOException | JWSInputException | VerificationException e ) {
				LOGGER.log( Level.FINE, "Unable to authenticate user with keycloak: " + username, e );
				throw new BadCredentialsException( "Unable to authenticate user with keycloak: " + username, e );
			}
		}

		throw new BadCredentialsException( "Unexpected authentication type: " + authentication );
	}
}
