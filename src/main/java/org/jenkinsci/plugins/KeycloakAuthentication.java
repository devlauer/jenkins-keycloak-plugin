package org.jenkinsci.plugins;

import hudson.security.SecurityRealm;

import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.providers.AbstractAuthenticationToken;
import org.keycloak.representations.IDToken;

public class KeycloakAuthentication extends AbstractAuthenticationToken  {


	private static final long serialVersionUID = 1L;
	private final String userName;
	
	public KeycloakAuthentication(IDToken idToken) {
		super(new GrantedAuthority[]{SecurityRealm.AUTHENTICATED_AUTHORITY});
		this.userName = idToken.getName();
		setAuthenticated(true);
	}
	
	@Override
	public String getName() {
		return this.userName;
	}

	@Override
	public Object getCredentials() {
		return ""; // do not expose the credential
	}

	@Override
	public Object getPrincipal() {
		return this.userName;
	}
}
