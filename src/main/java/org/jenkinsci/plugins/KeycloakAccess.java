package org.jenkinsci.plugins;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import hudson.security.GroupDetails;
import hudson.security.SecurityRealm;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.util.JsonSerialization;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;

public class KeycloakAccess {
	private static final Logger LOGGER = Logger.getLogger( KeycloakAccess.class.getName() );

	private KeycloakDeployment keycloakDeployment;

	public KeycloakAccess( KeycloakDeployment keycloakDeployment ) {
		this.keycloakDeployment = keycloakDeployment;
	}

	public UserDetails loadUserByUsername( String username ) throws UsernameNotFoundException, DataAccessException {
		LOGGER.finer( "Requested User Details for: " + username );
		try {
			String token = getAuthToken();
			List<GrantedAuthority> authorities = getAuthorities(getRolesForUser( username, token ));
			return new KeycloakUserDetails( username, authorities.toArray( new GrantedAuthority[0] ) );
		} catch ( IOException e ) {
			LOGGER.log( Level.INFO, "Unable to get user information from Keycloak for: " + username, e );
			throw new DataRetrievalFailureException( "Unable to get user information from Keycloak for " + username, e );
		}
	}

	public GroupDetails loadGroupByGroupname( String groupName ) throws UsernameNotFoundException, DataAccessException {
		try {
			List<String> roles = getRoles();
			if ( roles.contains( groupName ) ) {
				return new GroupDetails() {
					@Override
					public String getName() {
						return groupName;
					}
				};
			} else {
				LOGGER.info( "Couldn't find role information for: " + groupName );
				throw new DataRetrievalFailureException( "Couldn't find role information for: " + groupName );
			}
		} catch ( IOException e ) {
			LOGGER.log( Level.INFO, "Unable to get role information from Keycloak for: " + groupName, e );
			throw new DataRetrievalFailureException( "Unable to get role information from Keycloak for: " + groupName, e );
		}
	}

	public AccessTokenResponse getAccessToken( String username, String password ) {
		Configuration configuration = new Configuration( keycloakDeployment.getAuthServerBaseUrl(), keycloakDeployment.getRealm(),
			keycloakDeployment.getResourceName(), keycloakDeployment.getResourceCredentials(), keycloakDeployment.getClient() );
		AuthzClient authzClient = AuthzClient.create( configuration );
		return authzClient.obtainAccessToken( username, password );
	}

	public UserDetails authenticate( String username, String password ) throws AuthenticationException {
		try {
			Configuration configuration = new Configuration( keycloakDeployment.getAuthServerBaseUrl(), keycloakDeployment.getRealm(),
				keycloakDeployment.getResourceName(), keycloakDeployment.getResourceCredentials(), keycloakDeployment.getClient() );
			AuthzClient authzClient = AuthzClient.create( configuration );
			AccessTokenResponse accessTokenResponse = authzClient.obtainAccessToken( username, password );
			String token = accessTokenResponse.getToken();
			List<GrantedAuthority> authorities = getAuthorities(getRolesForUser( username, token ));
			return new KeycloakUserDetails( username, authorities.toArray( new GrantedAuthority[0] ) );
		} catch ( IOException e ) {
			LOGGER.log( Level.INFO, "Unable to get Keycloak authenticate: " + username, e );
			throw new DataRetrievalFailureException( "Unable to get Keycloak authenticate: " + username, e );
		}
	}

	private String[] getRolesForUser( String username, String token ) throws IOException {
		String usersBaseUrl = keycloakDeployment.getAuthServerBaseUrl() + "/admin/realms/" + keycloakDeployment.getRealm() + "/users";
		String realmInfoUrl = usersBaseUrl + "?username=" + username;
		HttpGet getUsersReq = new HttpGet( realmInfoUrl );
		KeycloakAccess.addKeycloakClientCredentialsToRequest( getUsersReq, token );

		HttpResponse usersResponse = keycloakDeployment.getClient().execute( getUsersReq );
		int statusCode = usersResponse.getStatusLine().getStatusCode();
		if ( statusCode != 200 ) {
			LOGGER.info( "Unable to get user from Keycloak, status: " + statusCode + " : " + usersResponse.getStatusLine().getReasonPhrase() );
			throw new DataRetrievalFailureException(
				"Unable to get user from Keycloak, status: " + statusCode + " : " + usersResponse.getStatusLine().getReasonPhrase() );
		}

		HttpEntity httpEntity = usersResponse.getEntity();
		String userOutput = EntityUtils.toString( httpEntity );
		LOGGER.finest( "User Info output: " + userOutput );

		String id = getIdFromJson( userOutput );
		if ( id == null ) {
			throw new UsernameNotFoundException( "Unable to find username: " + username );
		}

		LOGGER.finer( "Getting roles for " + username + "(" + id + ")" );

		String roleUrl = usersBaseUrl + "/" + id + "/role-mappings";
		HttpGet roleMappingReq = new HttpGet( roleUrl );
		KeycloakAccess.addKeycloakClientCredentialsToRequest( roleMappingReq, token );
		HttpResponse roleMappingResponse = keycloakDeployment.getClient().execute( roleMappingReq );
		statusCode = roleMappingResponse.getStatusLine().getStatusCode();
		if ( statusCode != 200 ) {
			LOGGER.warning( "Unable to get role-mapping from Keycloak, status: " + statusCode + " : " + usersResponse.getStatusLine().getReasonPhrase() );
			throw new DataRetrievalFailureException(
				"Unable to get role-mapping from Keycloak, status: " + statusCode + " : " + usersResponse.getStatusLine().getReasonPhrase() );
		}
		httpEntity = roleMappingResponse.getEntity();
		String roleMappingOutput = EntityUtils.toString( httpEntity );
		LOGGER.finest( "Role Mapping output: " + roleMappingOutput );

		return getRolesFromJson( roleMappingOutput );
	}

	private List<String> getRoles() throws IOException {
		String token = getAuthToken();

		String rolesUrl = keycloakDeployment.getAuthServerBaseUrl() + "/admin/realms/" + keycloakDeployment.getRealm() + "/roles";
		HttpGet rolesReq = new HttpGet( rolesUrl );
		KeycloakAccess.addKeycloakClientCredentialsToRequest( rolesReq, token );

		HttpResponse usersResponse = keycloakDeployment.getClient().execute( rolesReq );
		int statusCode = usersResponse.getStatusLine().getStatusCode();
		if ( statusCode != 200 ) {
			LOGGER.info( "Unable to get roles from Keycloak, status: " + statusCode + " : " + usersResponse.getStatusLine().getReasonPhrase() );
			throw new DataRetrievalFailureException(
				"Unable to get roles from Keycloak, status: " + statusCode + " : " + usersResponse.getStatusLine().getReasonPhrase() );
		}

		HttpEntity httpEntity = usersResponse.getEntity();
		String roleOutput = EntityUtils.toString( httpEntity );
		LOGGER.finest( "Roles output: " + roleOutput );

		return getRoleInfoFromJson( roleOutput );
	}

	private String getAuthToken() {
		Configuration configuration = new Configuration( keycloakDeployment.getAuthServerBaseUrl(), keycloakDeployment.getRealm(),
			keycloakDeployment.getResourceName(), keycloakDeployment.getResourceCredentials(), keycloakDeployment.getClient() );
		AuthzClient authzClient = AuthzClient.create( configuration );
		AccessTokenResponse accessTokenResponse = authzClient.obtainAccessToken();
		return accessTokenResponse.getToken();
	}

	private String[] getRolesFromJson( String json ) throws JsonProcessingException {
		List<String> roles = new ArrayList<>();
		JsonNode jsonDocNode = JsonSerialization.mapper.readTree( json );
		JsonNode realmMappingsArr = jsonDocNode.get( "realmMappings" );
		if ( realmMappingsArr != null ) {
			Iterator<JsonNode> roleMappingsIT = realmMappingsArr.elements();
			while (roleMappingsIT.hasNext()) {
				JsonNode roleMapping = roleMappingsIT.next();
				JsonNode nameNode = roleMapping.get( "name" );
				if ( nameNode != null ) {
					roles.add( nameNode.asText() );
				}
			}
		}
		return roles.toArray( new String[0] );
	}

	private String getIdFromJson( String json ) throws JsonProcessingException {
		JsonNode userInfoArrNode = JsonSerialization.mapper.readTree( json );
		Iterator<JsonNode> nodeIT = userInfoArrNode.elements();
		if ( nodeIT.hasNext() ) {
			JsonNode userNode = nodeIT.next();
			JsonNode idNode = userNode.get( "id" );
			if ( idNode != null ) {
				return idNode.asText();
			}
		}
		return null;
	}

	private List<String> getRoleInfoFromJson( String json ) throws JsonProcessingException {
		List<String> roles = new ArrayList<>();

		JsonNode rolesArrNode = JsonSerialization.mapper.readTree( json );
		Iterator<JsonNode> nodeIT = rolesArrNode.elements();
		while (nodeIT.hasNext()) {
			JsonNode roleNode = nodeIT.next();
			JsonNode nameNode = roleNode.get( "name" );
			if ( nameNode != null ) {
				roles.add( nameNode.asText() );
			}
		}
		return roles;
	}

	private static void addKeycloakClientCredentialsToRequest( HttpRequest request, String token ) {
		request.setHeader( "Authorization", "Bearer " + token );
	}

	private static List<GrantedAuthority> getAuthorities(String[] roles) {
		List<GrantedAuthority> authorities = new ArrayList<>();
		authorities.add( SecurityRealm.AUTHENTICATED_AUTHORITY );
		for ( String role : roles ) {
			authorities.add( new GrantedAuthorityImpl( role ) );
		}
		return authorities;
	}
}
