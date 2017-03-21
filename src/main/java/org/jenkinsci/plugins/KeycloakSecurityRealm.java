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
import hudson.security.SecurityRealm;
import hudson.tasks.Mailer;

import java.io.IOException;
import java.security.PublicKey;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.context.SecurityContextHolder;
import org.keycloak.OAuth2Constants;
import org.keycloak.RSATokenVerifier;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.ServerRequest;
import org.keycloak.adapters.ServerRequest.HttpFailure;
import org.keycloak.adapters.rotation.AdapterRSATokenVerifier;
import org.keycloak.common.util.KeycloakUriBuilder;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.keycloak.util.JsonSerialization;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Header;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * Implementation of the AbstractPasswordBasedSecurityRealm that uses keycloak
 * oauth for sso.
 *
 * This is based on the MySQLSecurityRealm from the mysql-auth-plugin written by
 * Alex Ackerman.
 * 
 * @author Mohammad Nadeem
 */
public class KeycloakSecurityRealm extends SecurityRealm {

	private static final String JENKINS_LOGIN_URL = "securityRealm/commenceLogin";
	private static final String JENKINS_LOG_OUT_URL = "securityRealm/finishLogin";

	private static final Logger LOGGER = Logger.getLogger(KeycloakSecurityRealm.class.getName());

	private static final String REFERER_ATTRIBUTE = KeycloakSecurityRealm.class.getName() + ".referer";

	private transient KeycloakDeployment keycloakDeployment;
	private String keycloakJson;

	@DataBoundConstructor
	public KeycloakSecurityRealm(String keycloakJson) throws IOException {
		super();
		this.keycloakJson = keycloakJson;
		LOGGER.info(keycloakJson);
	}

	
	
	public HttpResponse doCommenceLogin(StaplerRequest request, StaplerResponse response,
			@Header("Referer") final String referer) throws IOException {
		request.getSession().setAttribute(REFERER_ATTRIBUTE, referer);

		String redirect = redirectUrl(request);

		String state = UUID.randomUUID().toString();

		String authUrl = getKeycloakDeployment().getAuthUrl().clone()
				.queryParam(OAuth2Constants.CLIENT_ID, getKeycloakDeployment().getResourceName())
				.queryParam(OAuth2Constants.REDIRECT_URI, redirect)
				.queryParam(OAuth2Constants.STATE, state)
				.queryParam(OAuth2Constants.RESPONSE_TYPE, OAuth2Constants.CODE)
				.build()
				.toString();

		return new HttpRedirect(authUrl);

	}

	private String redirectUrl(StaplerRequest request) {
		String refererURL = request.getReferer();
		String requestURL = request.getRequestURL().toString();
		//if a reverse proxy with ssl is used, the redirect should point to https
		if(refererURL!=null&&requestURL!=null&&refererURL.startsWith("https:")&&requestURL.startsWith("http:"))
		{
			requestURL = requestURL.replace("http:", "https:");
		}
		KeycloakUriBuilder builder = KeycloakUriBuilder.fromUri(requestURL)
				.replacePath(request.getContextPath()).replaceQuery(null).path(JENKINS_LOG_OUT_URL);
		String redirect = builder.toTemplate();
		return redirect;
	}

	/**
	 * This is where the user comes back to at the end of the OpenID redirect
	 * ping-pong.
	 * 
	 * @throws HttpFailure
	 * @throws VerificationException
	 */
	public HttpResponse doFinishLogin(StaplerRequest request) {

		String redirect = redirectUrl(request);

		try {

			AccessTokenResponse tokenResponse = ServerRequest.invokeAccessCodeToToken(getKeycloakDeployment(),
					request.getParameter("code"), redirect, null);

			String tokenString = tokenResponse.getToken();
			String idTokenString = tokenResponse.getIdToken();
			String refreashToken = tokenResponse.getRefreshToken();

			AccessToken token = AdapterRSATokenVerifier.verifyToken(tokenString, getKeycloakDeployment());
			if (idTokenString != null) {
				JWSInput input = new JWSInput(idTokenString);

				IDToken idToken = input.readJsonContent(IDToken.class);
				SecurityContextHolder.getContext()
						.setAuthentication(new KeycloakAuthentication(idToken, token, refreashToken));

				User currentUser = User.current();
				if (currentUser != null) {
					currentUser.setFullName(idToken.getPreferredUsername());

					if (!currentUser.getProperty(Mailer.UserProperty.class).hasExplicitlyConfiguredAddress()) {
						currentUser.addProperty(new Mailer.UserProperty(idToken.getEmail()));
					}
				}
			}

		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Authentication Exception ", e);
		}

		String referer = (String) request.getSession().getAttribute(REFERER_ATTRIBUTE);
		if (referer != null) {
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
		return new SecurityComponents(new AuthenticationManager() {
			public Authentication authenticate(Authentication authentication) throws AuthenticationException {
				if (authentication instanceof KeycloakAuthentication)
					return authentication;
				throw new BadCredentialsException("Unexpected authentication type: " + authentication);
			}
		});
	}

	@Override
	public String getLoginUrl() {
		return JENKINS_LOGIN_URL;
	}

	@Override
	public void doLogout(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
		KeycloakAuthentication keycloakAuthentication = (KeycloakAuthentication) SecurityContextHolder.getContext()
				.getAuthentication();
		try {
			ServerRequest.invokeLogout(getKeycloakDeployment(), keycloakAuthentication.getRefreashToken());
			super.doLogout(req, rsp);
		} catch (HttpFailure e) {
			LOGGER.log(Level.SEVERE, "Logout Exception ", e);
		}
	}

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

		public DescriptorImpl() {
			super();
		}

		public DescriptorImpl(Class<? extends SecurityRealm> clazz) {
			super(clazz);
		}
	}

	public String getKeycloakJson() {
		return keycloakJson;
	}

	public void setKeycloakJson(String keycloakJson) {
		this.keycloakJson = keycloakJson;
	}

	private synchronized KeycloakDeployment getKeycloakDeployment() throws IOException
	{
		if(keycloakDeployment==null||keycloakDeployment.getClient()==null)
		{
			AdapterConfig adapterConfig = JsonSerialization.readValue(keycloakJson, AdapterConfig.class);
			keycloakDeployment = KeycloakDeploymentBuilder.build(adapterConfig);
		}
		return keycloakDeployment;
	}
}
