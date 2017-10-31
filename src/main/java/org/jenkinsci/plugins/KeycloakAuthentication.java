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
 * @author dev.lauer@elnarion.de
 *
 */
public class KeycloakAuthentication extends AbstractAuthenticationToken  {


	private static final long serialVersionUID = 1L;
	private final String userName;
	private String refreshToken;
	
	/**
	 * Constructor
	 * @param idToken the keycloak id token
	 * @param accessToken the keycloak access token
	 * @param refreshToken the keycloak refresh token
	 */
	public KeycloakAuthentication(IDToken idToken, AccessToken accessToken, String refreshToken) {
		super(buildRoles(accessToken));
		this.userName = idToken.getPreferredUsername();
		this.refreshToken = refreshToken;
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

	/**
	 * Get the keycloak refresh token
	 * @return {@link String} the refresh token
	 */
	public String getRefreshToken() {
		return refreshToken;
	}
}
