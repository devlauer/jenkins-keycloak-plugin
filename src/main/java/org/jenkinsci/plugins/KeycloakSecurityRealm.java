/**
 The MIT License

 Copyright (c) 2011 Michael O'Cleirigh

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.



 */
package org.jenkinsci.plugins;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.User;
import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import hudson.security.SecurityRealm;
import hudson.tasks.Mailer;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.cert.X509Certificate;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import jenkins.security.SecurityListener;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.apache.commons.lang.StringUtils;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.OAuth2Constants;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.OIDCHttpFacade;
import org.keycloak.adapters.ServerRequest;
import org.keycloak.adapters.ServerRequest.HttpFailure;
import org.keycloak.adapters.rotation.AdapterTokenVerifier;
import org.keycloak.adapters.spi.AuthenticationError;
import org.keycloak.adapters.spi.LogoutError;
import org.keycloak.common.util.KeycloakUriBuilder;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.keycloak.util.JsonSerialization;
import org.keycloak.util.TokenUtil;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Header;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;

/**
 *
 * Implementation of the AbstractPasswordBasedSecurityRealm that uses keycloak
 * oauth for sso.
 *
 * This is based on the MySQLSecurityRealm from the mysql-auth-plugin written by
 * Alex Ackerman.
 *
 * @author Mohammad Nadeem, devlauer
 */
public class KeycloakSecurityRealm extends AbstractPasswordBasedSecurityRealm {

	private static final String JENKINS_LOGIN_URL = "securityRealm/commenceLogin";
	/**
	 * The default URL to finish the login process of this plugin
	 */
	public static final String JENKINS_FINISH_LOGIN_URL = "securityRealm/finishLogin";

	/**
	 * This constant is used to save the state of an authenticated session. If the
	 * login process starts it is set to true, if a logout process is initiated it
	 * is set to false.
	 */
	public static final String AUTH_REQUESTED = "AUTH_REQUESTED";

	private static final Logger LOGGER = Logger.getLogger(KeycloakSecurityRealm.class.getName());

	private static final String REFERER_ATTRIBUTE = KeycloakSecurityRealm.class.getName() + ".referer";

	private transient KeycloakDeployment keycloakDeployment;

	private transient RefreshFilter filter;

	private String keycloakJson = "";
	private String keycloakIdp = "";
	private boolean keycloakValidate = false;
	private boolean keycloakRespectAccessTokenTimeout = true;
	private boolean cacheEnabled = true;
	private String cacheSizeStr = "1000";
	private String cacheTtlSecStr = "300";

	/**
	 * Constructor
	 *
	 * @throws IOException
	 *             -
	 */
	@DataBoundConstructor
	public KeycloakSecurityRealm(String keycloakJson,
		String keycloakIdp,
		boolean keycloakValidate,
		boolean keycloakRespectAccessTokenTimeout,
		boolean cacheEnabled,
		String cacheSizeStr,
		String cacheTtlSecStr) throws IOException {
		super();
		setKeycloakJson( keycloakJson );
		setKeycloakIdp( keycloakIdp );
		setKeycloakValidate( keycloakValidate );
		setKeycloakRespectAccessTokenTimeout( keycloakRespectAccessTokenTimeout );
		setCacheEnabled( cacheEnabled );
		setCacheSizeStr( cacheSizeStr );
		setCacheTtlSecStr( cacheTtlSecStr );
		createFilter();
	}

	protected KeycloakSecurityRealm() {
		super();
		createFilter();
		KeycloakCache.getInstance().updateCacheConfiguration( isCacheEnabled(), getParsedCacheTtlSec(), getParsedCacheSize() );
	}

	/*
	 * hudson.security.SecurityRealm.createFilter(FilterConfig) extension point is
	 * not used to leave this kind of handling unchanged. Hence, we are sticking
	 * with the hudson.util.PluginServletFilter.addFilter(Filter) path.
	 */
	synchronized void createFilter() {
		// restarts on things like plugin upgrade bypassed the call to the
		// constructor, so filter initialization
		// has to be driven in-line; note, after initial bring up, the filter
		// variable will be set after subsequent
		// jenkins restarts, but the addFilter call needs to be made on each
		// restart, so we check flag to see if the filter
		// has been ran through at least once
		if (filter == null || !filter.isInitCalled()) {
			try {
				LOGGER.log(Level.INFO, "Create Filter");
				filter = new RefreshFilter();
				hudson.util.PluginServletFilter.addFilter(filter);
			} catch (ServletException e) {
				LOGGER.log(Level.SEVERE, "createFilter", e);
			}
		}
	}

	/**
	 * @param request
	 *            the Jenkins request
	 * @param response
	 *            the Jenkins response
	 * @param referer
	 *            the referrer
	 * @return {@link HttpResponse} the response
	 * @throws IOException
	 */
	public HttpResponse doCommenceLogin(StaplerRequest request, StaplerResponse response,
			@Header("Referer") final String referer) throws IOException {
		request.getSession().setAttribute(REFERER_ATTRIBUTE, referer);

		String scopeParam = TokenUtil.attachOIDCScope(null);
		String redirect = redirectUrl(request);

		String state = UUID.randomUUID().toString();

		KeycloakUriBuilder builder = getKeycloakDeployment().getAuthUrl().clone()
			.queryParam(OAuth2Constants.CLIENT_ID, getKeycloakDeployment().getResourceName())
			.queryParam(OAuth2Constants.REDIRECT_URI, redirect).queryParam(OAuth2Constants.STATE, state)
			.queryParam(OAuth2Constants.RESPONSE_TYPE, OAuth2Constants.CODE)
			.queryParam(OAuth2Constants.SCOPE, scopeParam);
		String keycloakIdp = getKeycloakIdp();
		if (!"".equals(keycloakIdp)&&(keycloakIdp!=null)) {
			builder.queryParam("kc_idp_hint", keycloakIdp);
		}
		String authUrl = builder.build().toString();
		request.getSession().setAttribute(AUTH_REQUESTED, Boolean.valueOf(true));
		createFilter();
		KeycloakCache cache = KeycloakCache.getInstance();
		if (!cache.isInitialized() ) {
			KeycloakCache.getInstance().updateCacheConfiguration( isCacheEnabled(), getParsedCacheTtlSec(), getParsedCacheSize() );
		}
		return new HttpRedirect(authUrl);

	}

	private String redirectUrl(StaplerRequest request) {
		String refererURL = request.getReferer();
		String requestURL = request.getRequestURL().toString();
		// if a reverse proxy with ssl is used, the redirect should point to
		// https
		if (refererURL != null && requestURL != null && refererURL.startsWith("https:")
				&& requestURL.startsWith("http:")) {
			requestURL = requestURL.replace("http:", "https:");
		}
		KeycloakUriBuilder builder = KeycloakUriBuilder.fromUri(requestURL).replacePath(request.getContextPath())
				.replaceQuery(null).path(JENKINS_FINISH_LOGIN_URL);
		String redirect = builder.toTemplate();
		return redirect;
	}

	private KeycloakDeployment resolveDeployment(KeycloakDeployment baseDeployment, HttpServletRequest request) {
		ServletFacade facade = new ServletFacade(request);
		return new AdapterDeploymentContext(baseDeployment).resolveDeployment(facade);
	}

	/**
	 * This is where the user comes back to at the end of the OpenID redirect
	 * ping-pong.
	 *
	 * @param request
	 *            the Jenkins request
	 * @return {@link HttpResponse} the response
	 * @throws IOException
	 */
	public HttpResponse doFinishLogin(StaplerRequest request) throws IOException {

		String redirect = redirectUrl(request);

		try {
			LOGGER.log(Level.FINE, "Code" + request.getParameter(OAuth2Constants.CODE));
			LOGGER.log(Level.FINE, "Redirect" + redirect);

			KeycloakDeployment resolvedDeployment = resolveDeployment(getKeycloakDeployment(), request);

			LOGGER.log(Level.FINE, "TokenURL" + resolvedDeployment.getTokenUrl());

			AccessTokenResponse tokenResponse = ServerRequest.invokeAccessCodeToToken(resolvedDeployment,
					request.getParameter(OAuth2Constants.CODE), redirect, null);

			String tokenString = tokenResponse.getToken();
			String idTokenString = tokenResponse.getIdToken();
			String refreshToken = tokenResponse.getRefreshToken();

			AccessToken token = AdapterTokenVerifier.verifyToken(tokenString, resolvedDeployment);
			if (idTokenString != null) {
				JWSInput input = new JWSInput(idTokenString);

				IDToken idToken = input.readJsonContent(IDToken.class);

				KeycloakAuthentication auth = new KeycloakAuthentication(idToken, token, refreshToken, tokenResponse);
				SecurityContextHolder.getContext().setAuthentication(auth);

				User currentUser = User.current();
				if (currentUser != null) {
					currentUser.setFullName(idToken.getPreferredUsername());

					if (!currentUser.getProperty(Mailer.UserProperty.class).hasExplicitlyConfiguredAddress()) {
						currentUser.addProperty(new Mailer.UserProperty(idToken.getEmail()));
					}

					KeycloakUserDetails userDetails = new KeycloakUserDetails(
							idToken.getPreferredUsername(), auth.getAuthorities()
					);
					SecurityListener.fireAuthenticated(userDetails);
				}
			}

		} catch (Exception e) {
			HttpFailure hf = null;
			LOGGER.log(Level.SEVERE, "Authentication Exception ", e);
			if (e instanceof HttpFailure) {
				hf = (HttpFailure) e;
			}
			Throwable cause = e.getCause();
			if (cause != null) {
				LOGGER.log(Level.SEVERE, "Original exception", cause);
				if (cause instanceof HttpFailure) {
					hf = (HttpFailure) cause;
				}
			}
			if (hf != null) {
				LOGGER.log(Level.SEVERE, "Failure Message" + ((HttpFailure) e).getError());
				LOGGER.log(Level.SEVERE, "Failure HTTP Status" + ((HttpFailure) e).getStatus());
			}

		}

		String referer = (String) request.getSession().getAttribute(REFERER_ATTRIBUTE);
		if (referer != null) {
			LOGGER.log(Level.FINEST, "Redirecting to " + referer);
			return HttpResponses.redirectTo(referer);
		}
		return HttpResponses.redirectToContextRoot();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see hudson.security.SecurityRealm#allowsSignup()
	 */
	@Override
	public boolean allowsSignup() {
		return false;
	}

	@Override
	public SecurityComponents createSecurityComponents() {
		KeycloakUserDetailsService userDetailsService = new KeycloakUserDetailsService(this);
		KeycloakAuthenticationManager authenticationManager = new KeycloakAuthenticationManager(this);
		SecurityComponents sc = new SecurityComponents(authenticationManager, userDetailsService);
		return sc;
	}

	@Override
	public String getLoginUrl() {
		return JENKINS_LOGIN_URL;
	}

	@Override
	public void doLogout(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof KeycloakAuthentication) {
			KeycloakAuthentication keycloakAuthentication = (KeycloakAuthentication) authentication;
			try {
				ServerRequest.invokeLogout(getKeycloakDeployment(), keycloakAuthentication.getRefreshToken());
			} catch (HttpFailure e) {
				LOGGER.log(Level.SEVERE, "Logout Exception ", e);
			}
		}
		req.getSession().setAttribute(AUTH_REQUESTED, Boolean.valueOf(false));
		super.doLogout(req, rsp);
	}

	@Override
	protected UserDetails authenticate( String username, String password ) throws AuthenticationException {
		LOGGER.info( "KeycloakSecurityRealm.authenticate: " + username );
		try {
			KeycloakAccess keycloakAccess = new KeycloakAccess( getKeycloakDeployment() );
			return keycloakAccess.authenticate( username, password );
		} catch ( IOException e ) {
			LOGGER.log( Level.WARNING, "Unable to authenticate: " + username, e );
			throw new DataRetrievalFailureException( "Unable to authenticate: " + username, e );
		}
	}

	@Override
	public UserDetails loadUserByUsername( String username ) throws UsernameNotFoundException, DataAccessException {
		return getSecurityComponents().userDetails.loadUserByUsername( username );
	}

	@Override
	public GroupDetails loadGroupByGroupname( String groupName ) throws UsernameNotFoundException, DataAccessException {
		LOGGER.info( "KeycloakSecurityRealm.loadGroupByGroupname: " + groupName );
		try {
			KeycloakAccess keycloakAccess = new KeycloakAccess( getKeycloakDeployment() );
			return keycloakAccess.loadGroupByGroupname( groupName );
		} catch ( IOException e ) {
			LOGGER.log( Level.WARNING, "Unable to get role information from Keycloak for: " + groupName, e );
			throw new DataRetrievalFailureException( "Unable to get role information from Keycloak for: " + groupName, e );
		}
	}

	/**
	 * Descriptor definition for Jenkins
	 *
	 * @author dev.lauer@elnarion.de
	 *
	 */
	@Extension
	public static final class DescriptorImpl extends Descriptor<SecurityRealm> {
		@Override
		public String getHelpFile() {
			return "/plugin/keycloak/help/help-security-realm.html";
		}

		@Override
		public String getDisplayName() {
			return "Keycloak Authentication Plugin";
		}

		/**
		 * Constructor
		 */
		public DescriptorImpl() {
			super();
		}

		public DescriptorImpl(Class<? extends SecurityRealm> clazz) {
			super(KeycloakSecurityRealm.class);
		}

		/**
		 * Validate keycloakJson
		 *
		 * @param value String the form field value to validate
		 * @return {@link FormValidation} the validation result
		 * @throws ServletException
		*/
		public FormValidation doCheckKeycloakJson(@QueryParameter String value) throws ServletException {
			try {
				if (value != null && !value.isEmpty()) {
					JsonSerialization.readValue(value, AdapterConfig.class);
				}
			} catch (IOException ex) {
				return FormValidation.error("Invalid adapter config");
			}
			return FormValidation.ok();
		}
	}

	/**
	 * Returns the keycloak configuration
	 *
	 * @return {@link String} the configuration string
	 */
	public String getKeycloakJson() {
		return keycloakJson;
	}

	/**
	 * Sets the keycloak json configuration string
	 *
	 * @param keycloakJson
	 *            the configuration
	 */
	public void setKeycloakJson(String keycloakJson) {
		this.keycloakJson = keycloakJson;
	}

	/**
	 * Returns the configuration parameter for the authentication check on each
	 * request
	 *
	 * @return {@link Boolean} if true, authentication is checked on each request
	 */
	public boolean isKeycloakValidate() {
		return keycloakValidate;
	}

	/**
	 * Sets the configuration parameter for the authentication check
	 *
	 * @param keycloakValidate
	 *            {@link Boolean} if true authentication is checked on each request
	 */
	public void setKeycloakValidate(boolean keycloakValidate) {
		this.keycloakValidate = keycloakValidate;
	}

	/**
	 * Returns the configuration parameter for the access token check
	 *
	 * @return {@link Boolean} whether the expiration of the access token should be
	 *         checked or not before a token refresh
	 */
	public boolean isKeycloakRespectAccessTokenTimeout() {
		return keycloakRespectAccessTokenTimeout;
	}

	/**
	 * Sets the configuration parameter for the access token check
	 *
	 * @param keycloakRespectAccessTokenTimeout
	 *            {@link Boolean} whether the expiration of the access token should
	 *            be checked or not before a token refresh
	 */
	public void setKeycloakRespectAccessTokenTimeout(boolean keycloakRespectAccessTokenTimeout) {
		this.keycloakRespectAccessTokenTimeout = keycloakRespectAccessTokenTimeout;
	}

	/**
	 * Returns the keycloak idp hint.
	 *
	 * @return {@link String} the keycloak idp hint
	 */
	public String getKeycloakIdp() {
		return keycloakIdp;
	}

	/**
	 * Sets the keycloak idp hint.
	 *
	 * @param keycloakIdp {@link String} the keycloak idp hint
	 *
	 */
	public void setKeycloakIdp(String keycloakIdp) {
		this.keycloakIdp = keycloakIdp;
	}

	/**
	 * Returns true if authentication should be checked on each response
	 *
	 * @return {@link Boolean}
	 */
	public boolean checkKeycloakOnEachRequest() {
		return isKeycloakValidate();
	}

	/**
	 * Returns true if the access token should be only refreshed after its timeout
	 *
	 * @return {@link Boolean}
	 */
	public boolean respectAccessTokenTimeout() {
		return isKeycloakRespectAccessTokenTimeout();
	}

	/**
	 * Returns the current KeycloakDeployment configuration.
	 *
	 * @return {@link KeycloakDeployment} the keycloak configuration
	 * @throws IOException
	 */
	public synchronized KeycloakDeployment getKeycloakDeployment() throws IOException {
		if (keycloakDeployment == null || keycloakDeployment.getClient() == null) {
			AdapterConfig adapterConfig = JsonSerialization.readValue(getKeycloakJson(), AdapterConfig.class);
			keycloakDeployment = KeycloakDeploymentBuilder.build(adapterConfig);
		}
		return keycloakDeployment;
	}

	/**
	 * @author dev.lauer@elnarion.de
	 *
	 */
	public static class ServletFacade implements OIDCHttpFacade {

		private final HttpServletRequest servletRequest;

		private ServletFacade(HttpServletRequest servletRequest) {
			this.servletRequest = servletRequest;
		}

		@Override
		public KeycloakSecurityContext getSecurityContext() {
			throw new IllegalStateException("Not yet implemented");
		}

		@Override
		public Request getRequest() {
			return new Request() {

				@Override
				public String getFirstParam(String param) {
					return servletRequest.getParameter(param);
				}

				@Override
				public String getMethod() {
					return servletRequest.getMethod();
				}

				@Override
				public String getURI() {
					return servletRequest.getRequestURL().toString();
				}

				@Override
				public String getRelativePath() {
					return servletRequest.getServletPath();
				}

				@Override
				public boolean isSecure() {
					return servletRequest.isSecure();
				}

				@Override
				public String getQueryParamValue(String param) {
					return servletRequest.getParameter(param);
				}

				@Override
				public Cookie getCookie(String cookieName) {
					return null;
				}

				@Override
				public String getHeader(String name) {
					return servletRequest.getHeader(name);
				}

				@Override
				public List<String> getHeaders(String name) {
					return null;
				}

				@Override
				public InputStream getInputStream() {
					try {
						return servletRequest.getInputStream();
					} catch (IOException ioe) {
						throw new RuntimeException(ioe);
					}
				}

				@Override
				public String getRemoteAddr() {
					return servletRequest.getRemoteAddr();
				}

				@Override
				public void setError(AuthenticationError error) {
					servletRequest.setAttribute(AuthenticationError.class.getName(), error);

				}

				@Override
				public void setError(LogoutError error) {
					servletRequest.setAttribute(LogoutError.class.getName(), error);
				}

				@Override
				public InputStream getInputStream(boolean buffered) {
					try {
						return servletRequest.getInputStream();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}

			};
		}

		@Override
		public Response getResponse() {
			throw new IllegalStateException("Not yet implemented");
		}

		@Override
		public X509Certificate[] getCertificateChain() {
			throw new IllegalStateException("Not yet implemented");
		}
	}

	public boolean isCacheEnabled() {
		return cacheEnabled;
	}

	public void setCacheEnabled( boolean cacheEnabled ) {
		this.cacheEnabled = cacheEnabled;
		KeycloakCache.getInstance().updateCacheConfiguration( isCacheEnabled(), getParsedCacheTtlSec(), getParsedCacheSize() );
	}

	public int getParsedCacheSize() {
		int cacheSize = 1000;
		if ( StringUtils.isNotBlank(cacheSizeStr)) {
			try {
				cacheSize = Integer.parseInt( cacheSizeStr );
			} catch (NumberFormatException nfe) {
				LOGGER.info( "Unable to parse configuration for cache size, must be a number: " + cacheSizeStr );
			}
		}
		return cacheSize;
	}

	public void setCacheSizeStr( String cacheSizeStr ) {
		this.cacheSizeStr = cacheSizeStr;
		KeycloakCache.getInstance().updateCacheConfiguration( isCacheEnabled(), getParsedCacheTtlSec(), getParsedCacheSize() );
	}

	public long getParsedCacheTtlSec() {
		int cacheTtl = 300;
		if ( StringUtils.isNotBlank(cacheTtlSecStr)) {
			try {
				cacheTtl = Integer.parseInt( cacheTtlSecStr );
			} catch (NumberFormatException nfe) {
				LOGGER.info( "Unable to parse configuration for cache ttl, must be a number: " + cacheTtlSecStr );
			}
		}
		return cacheTtl;
	}

	public String getCacheSizeStr() {
		return cacheSizeStr;
	}

	public String getCacheTtlSecStr() {
		return cacheTtlSecStr;
	}

	public void setCacheTtlSecStr( String cacheTtlSecStr ) {
		this.cacheTtlSecStr = cacheTtlSecStr;
		KeycloakCache.getInstance().updateCacheConfiguration( isCacheEnabled(), getParsedCacheTtlSec(), getParsedCacheSize() );

	}
}