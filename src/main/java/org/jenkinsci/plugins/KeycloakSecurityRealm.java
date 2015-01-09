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
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.User;
import hudson.security.SecurityRealm;
import hudson.tasks.Mailer;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.keycloak.adapters.ServerRequest.HttpFailure;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.keycloak.servlet.ServletOAuthClient;
import org.keycloak.servlet.ServletOAuthClientBuilder;
import org.keycloak.util.JsonSerialization;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Header;
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

	/**
	 * Logger for debugging purposes.
	 */
	private static final Logger LOGGER = Logger.getLogger(KeycloakSecurityRealm.class.getName());

    private static final String REFERER_ATTRIBUTE = KeycloakSecurityRealm.class.getName()+".referer";

	private String keycloakJson;
	private ServletOAuthClient client;

	/**
	 * @param githubWebUri The URI to the root of the web UI for GitHub or GitHub Enterprise,
	 *                     including the protocol (e.g. https).
	 * @param githubApiUri The URI to the root of the API for GitHub or GitHub Enterprise,
	 *                     including the protocol (e.g. https).
	 * @param clientID The client ID for the created OAuth Application.
	 * @param clientSecret The client secret for the created GitHub OAuth Application.
	 * @throws IOException 
	 */
	@DataBoundConstructor
	public KeycloakSecurityRealm(String keycloakJson) throws IOException {
		super();
		LOGGER.info(keycloakJson);
		this.keycloakJson = Util.fixEmptyAndTrim(keycloakJson);
		AdapterConfig adapterConfig = JsonSerialization.readValue(keycloakJson, AdapterConfig.class);
		this.client = ServletOAuthClientBuilder.build(adapterConfig);
		this.client.start();
	}

	public String getKeycloakJson() {
		return keycloakJson;
	}

	public void setKeycloakJson(String keycloakJson) {
		this.keycloakJson = keycloakJson;
	}

	public void doCommenceLogin(StaplerRequest request, StaplerResponse response, @Header("Referer") final String referer)
			throws IOException {

		LOGGER.info("doCommenceLogin");
		request.getSession().setAttribute(REFERER_ATTRIBUTE,referer);
		this.client.redirectRelative("securityRealm/finishLogin", request, response);
	}

	/**
	 * This is where the user comes back to at the end of the OpenID redirect
	 * ping-pong.
	 */
	public HttpResponse doFinishLogin(StaplerRequest request)
			throws IOException {

		String code = request.getParameter("code");
		LOGGER.info("code = " +code);
		if (code == null || code.trim().length() == 0) {
			LOGGER.info("doFinishLogin: missing code.");
			return HttpResponses.redirectToContextRoot();
		}

		try {
			AccessTokenResponse accessToken = this.client.getBearerToken(request);
			 if (accessToken.getIdToken() != null) {
		            IDToken idToken = ServletOAuthClient.extractIdToken(accessToken.getIdToken());

					SecurityContextHolder.getContext().setAuthentication(new KeycloakAuthentication(idToken));

					User u = User.current();
					u.setFullName(idToken.getPreferredUsername());

				    if (!u.getProperty(Mailer.UserProperty.class).hasExplicitlyConfiguredAddress()) {
					    u.addProperty(new Mailer.UserProperty(idToken.getEmail()));
				    }
			} else {
				LOGGER.info("keycloak did not return an access token.");
			}
		} catch (HttpFailure e) {
			LOGGER.log(Level.SEVERE, "HttpFailure ", e);
		}		

        String referer = (String)request.getSession().getAttribute(REFERER_ATTRIBUTE);
        if (referer!=null) {
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
        return new SecurityComponents(
            new AuthenticationManager() {
                public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                    if (authentication instanceof AnonymousAuthenticationToken)
                        return authentication;
                    throw new BadCredentialsException("Unexpected authentication type: " + authentication);
                }
            }
        );
    }

	@Override
	public String getLoginUrl() {
		return "securityRealm/commenceLogin";
	}
	
	@Override
	public void doLogout(StaplerRequest req, StaplerResponse rsp)throws IOException, ServletException {
		super.doLogout(req, rsp);
	}

	@Extension
	public static final class DescriptorImpl extends Descriptor<SecurityRealm> {

		@Override
		public String getHelpFile() {
			return "/plugin/keycloak-oauth/help/help-security-realm.html";
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
}
