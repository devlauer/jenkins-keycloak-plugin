package org.jenkinsci.plugins;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.ServerRequest;
import org.keycloak.adapters.ServerRequest.HttpFailure;
import org.keycloak.representations.AccessTokenResponse;

import hudson.security.SecurityRealm;
import jenkins.model.Jenkins;

/**
 * Filter to check the validity of the token
 * 
 * @author dev.lauer@elnarion.de
 *
 */
public class RefreshFilter implements Filter {

	private static final Logger LOGGER = Logger.getLogger(RefreshFilter.class.getName());

	/**
	 * Constructor
	 */
	public RefreshFilter() {
	}

	private transient boolean initCalled = false;

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		initCalled = true;
	}

	private boolean skipUrl(HttpServletRequest paramRequest) {
		boolean result = false;
		String pathInfo = paramRequest.getPathInfo();
		LOGGER.log(Level.FINEST, "Path" + pathInfo);
		if (pathInfo != null) {
			result = (pathInfo.endsWith("/logout"))
					|| pathInfo.endsWith(KeycloakSecurityRealm.JENKINS_FINISH_LOGIN_URL);
		}
		return result;
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
			throws IOException, ServletException {
		LOGGER.log(Level.FINER, "KeycloakFilter entered");
		Jenkins j = Jenkins.getActiveInstance();
		if (j != null) {
			SecurityRealm sr = j.getSecurityRealm();
			if (sr != null) {
				// only if an instance of KeycloakSecurityRealm is active check token validity
				if (sr instanceof KeycloakSecurityRealm) {
					KeycloakSecurityRealm ksr = (KeycloakSecurityRealm) sr;
					LOGGER.log(Level.FINER, "KeycloakSecurityRealm found");
					boolean checkTokenValidity = ksr.checkKeycloakOnEachRequest();
					HttpServletRequest httpRequest = (HttpServletRequest) req;
					HttpSession session = httpRequest.getSession();
					Boolean authRequestedAttribute = (Boolean) session
							.getAttribute(KeycloakSecurityRealm.AUTH_REQUESTED);
					boolean authenticationRequested = (authRequestedAttribute == null) ? false
							: authRequestedAttribute.booleanValue();
					boolean skipUrl = skipUrl(httpRequest);
					LOGGER.log(Level.FINEST,
							"RequestPath" + httpRequest.getPathInfo() + " skipUrl" + skipUrl
									+ " AuthenticationRequested" + authenticationRequested + " CheckRequest"
									+ checkTokenValidity);
					// only if a check is configured and the user already logged in and the
					// requested URL does not end with logout do filtering
					if (checkTokenValidity && !skipUrl && authenticationRequested) {
						boolean tokeninvalid = checkTokenValidity(res, ksr);
						if (tokeninvalid)
							return;
					}
					// normal processing
					chain.doFilter(req, res);
				}
			}
		}
	}

	private boolean checkTokenValidity(ServletResponse res, KeycloakSecurityRealm ksr) throws IOException {
		boolean tokeninvalid = false;
		LOGGER.log(Level.FINE, "KeycloakFilter is active");
		KeycloakDeployment kd = ksr.getKeycloakDeployment();
		SecurityContext sc = SecurityContextHolder.getContext();
		if (sc != null) {
			Authentication auth = sc.getAuthentication();
			if (auth instanceof KeycloakAuthentication) {
				KeycloakAuthentication ka = (KeycloakAuthentication) auth;
				// if the refreshToken is already expired, it can not be used anymore
				// so automatically log out
				if (ka.isRefreshExpired()) {
					LOGGER.log(Level.FINE,
							"Keycloak refresh token is expired. Refresh token expiry "
									+ ka.getAccessTokenResponse().getRefreshExpiresIn() + " seconds. Last refresh "
									+ ka.getLastRefresh() + ". Current Time " + new Date());
					tokeninvalid = true;
					redirectToJenkinsLogoutUrl(res);
				}
				try {
					boolean respectAccessTokenTimeout = ksr.respectAccessTokenTimeout();
					Calendar secondsCheck = Calendar.getInstance();
					secondsCheck.add(Calendar.SECOND, -1);
					boolean newRefresh = secondsCheck.after(ka.getLastRefreshDateAsCalendar());
					boolean accessTokenExpired = ka.isAccessExpired();
					// if the access token timeout should be respected and it is expired then
					// refresh it
					// or
					// if the access token timeout should not be respected, but the last refresh is
					// older than 1 second then refresh it.
					if ((respectAccessTokenTimeout && accessTokenExpired)
							|| (!respectAccessTokenTimeout && newRefresh)) {
						LOGGER.log(Level.FINE,
								"KeycloakFilter refresh token. Respect access token timeout: "
										+ respectAccessTokenTimeout + ". Access token expired " + accessTokenExpired
										+ ". Renew after 1 second:" + newRefresh);
						AccessTokenResponse atr = ServerRequest.invokeRefresh(kd, ka.getRefreshToken());
						ka.setAccessTokenResponse(atr);
					}
				} catch (HttpFailure e) {
					LOGGER.log(Level.INFO, "Refresh Token failed, message is: " + e.getMessage() + ", error is:"
							+ e.getError() + ", statuscode is:" + e.getStatus());
					tokeninvalid = true;
					redirectToJenkinsLogoutUrl(res);
				}
			}
		}
		return tokeninvalid;
	}

	private void redirectToJenkinsLogoutUrl(ServletResponse res) throws IOException {
		//reset everything done before and redirect
		res.reset();
		Jenkins j = Jenkins.getActiveInstance();
		HttpServletResponse httpRes = (HttpServletResponse) res;
		LOGGER.log(Level.INFO, "KeycloakFilter logout requested");
		String rootURL = j.getRootUrl();
		if (rootURL == null)
			rootURL = "";
		String redirectURL = rootURL + "logout";
		LOGGER.log(Level.INFO, "Redirect to " + redirectURL);
		httpRes.sendRedirect(redirectURL);
	}

	@Override
	public void destroy() {
	}

	/**
	 * Returns whether the initialization method of this filter is already called.
	 * 
	 * @return true or false
	 */
	public boolean isInitCalled() {
		return initCalled;
	}

}
