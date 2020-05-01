package org.jenkinsci.plugins;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import hudson.security.SecurityRealm;

import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.providers.AbstractAuthenticationToken;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
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
	private String accessToken;
	private transient AccessTokenResponse accessTokenResponse = null;
	private Calendar lastRefresh = Calendar.getInstance();
	
	/**
	 * Constructor
	 * @param idToken the keycloak id token
	 * @param accessToken the keycloak access token
	 * @param refreshToken the keycloak refresh token
	 * @param tokenResponse the {@link AccessTokenResponse}
	 */
	public KeycloakAuthentication(IDToken idToken, AccessToken accessToken, String refreshToken, AccessTokenResponse tokenResponse) {
		super(buildRoles(accessToken));
		this.userName = idToken.getPreferredUsername();
		this.setRefreshToken(refreshToken);
		this.setAccessTokenResponse(tokenResponse);
		setAuthenticated(true);
	}

	@SuppressWarnings("unchecked")
	private static GrantedAuthority[] buildRoles(AccessToken accessToken) {
		List<GrantedAuthority> roles;
		roles = new ArrayList<GrantedAuthority>();

		if (accessToken != null && accessToken.getRealmAccess() != null) {
			for (String role : accessToken.getRealmAccess().getRoles()) {
				roles.add(new GrantedAuthorityImpl(role));
			}
		}

		if(accessToken != null && accessToken.getOtherClaims().containsKey("roles")) {
			for(String role : (List<String>) accessToken.getOtherClaims().get("roles")) {
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

	/**
	 * Get the keycloak access token
	 * @return {@link String} the access token
	 */
	public String getAccessToken() {
		return accessToken;
	}

	/**
	 * Sets the refresh token
	 * @param refreshToken {@link String}
	 */
	public void setRefreshToken(String refreshToken) {
		this.refreshToken = refreshToken;
	}

	/**
	 * Sets the access token
	 * @param accessToken {@link String}
	 */
	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	/**
	 * Returns the latest AccessTokenResponse
	 * 
	 * @return {@link AccessTokenResponse}
	 */
	public AccessTokenResponse getAccessTokenResponse() {
		return accessTokenResponse;
	}

	/**
	 * Sets the latest AccessTokenResponse
	 * @param accessTokenResponse
	 */
	public void setAccessTokenResponse(AccessTokenResponse accessTokenResponse) {
		this.accessTokenResponse = accessTokenResponse;
		setRefreshToken(accessTokenResponse.getRefreshToken());
		setLastRefresh(new Date());
	}

	/**
	 * Get the date the token is from
	 * @return {@link Date}
	 */
	public Date getLastRefresh() {
		return lastRefresh.getTime();
	}

	/**
	 * Get the date the token is from
	 * @return {@link Calendar}
	 */
	public Calendar getLastRefreshDateAsCalendar() {
		return lastRefresh;
	}
	/**
	 * Set the date the token is from
	 * @param lastRefresh
	 */
	public void setLastRefresh(Date lastRefresh) {
		this.lastRefresh.setTime(lastRefresh);
	}
	
	/**
	 * Checks whether the refresh token is expired or not.
	 * 
	 * @return boolean - the result of the check
	 */
	public boolean isRefreshExpired()
	{
		if(accessTokenResponse==null)
			return true;
		Calendar compareDate = Calendar.getInstance();
		compareDate.add(Calendar.SECOND, (int)(accessTokenResponse.getRefreshExpiresIn())*-1);
		return compareDate.after(lastRefresh);
	}
	
	/**
	 * Checks whether the access token is expired or not.
	 * 
	 * @return boolean - the result of the check
	 */
	public boolean isAccessExpired()
	{
		if(accessTokenResponse==null)
			return true;
		Calendar compareDate = Calendar.getInstance();
		compareDate.add(Calendar.SECOND, (int)(accessTokenResponse.getExpiresIn())*-1);
		return compareDate.after(lastRefresh);
	}	

}
