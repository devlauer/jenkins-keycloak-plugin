package org.jenkinsci.plugins;

import java.util.ArrayList;
import java.util.List;

import hudson.security.SecurityRealm;

import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.providers.AbstractAuthenticationToken;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.IDToken;
/**
 * 
 * @author Mohammad Nadeem
 *
 */
public class KeycloakAuthentication extends AbstractAuthenticationToken  {


	private static final long serialVersionUID = 1L;
	private final String userName;
	private String refreashToken;
	
	public KeycloakAuthentication(IDToken idToken, AccessToken accessToken, String refreashToken) {
		super(buildRoles(accessToken));
		this.userName = idToken.getName();
		this.refreashToken = refreashToken;
		setAuthenticated(true);
	}

	private static GrantedAuthority[] buildRoles(AccessToken accessToken) {
		List<GrantedAuthority> roles;
		roles = new ArrayList<GrantedAuthority>();
		if (accessToken != null && accessToken.getRealmAccess() != null) {
			for (String role : accessToken.getRealmAccess().getRoles()) {
				roles.add(new GrantedAuthorityImpl(role));
			}
		}
		roles.add(SecurityRealm.AUTHENTICATED_AUTHORITY);
		return roles.toArray(new GrantedAuthority[roles.size()]);
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

	public String getRefreashToken() {
		return refreashToken;
	}
}
