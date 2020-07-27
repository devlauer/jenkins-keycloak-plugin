package org.jenkinsci.plugins;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class KeycloakCache {
	private static KeycloakCache instance = new KeycloakCache();

	private Map<String, CacheEntry<Collection<String>>> rolesByUserCache = new CacheMap<>(1000);

	private CacheEntry<Collection<String>> roleCache = null;

	private Map<String, CacheEntry<Boolean>> invalidUserMap = new CacheMap<>(1000);

	private int ttlSec = (int)TimeUnit.MINUTES.toSeconds( 5 );

	private boolean enabled = true;

	private boolean initialized = false;

	private String token = null;

	private long tokenExpiration = 0;

	private static final Object TOKEN_LOCK = new Object();

	private static final Logger LOGGER = Logger.getLogger( KeycloakCache.class.getName() );

	public static KeycloakCache getInstance() {
		return instance;
	}

	public boolean isInitialized() {
		return initialized;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void updateCacheConfiguration(boolean enabled, long ttl, int cacheSize) {
		this.enabled = enabled;
		this.ttlSec = (int)ttl;
		((CacheMap)rolesByUserCache).setSize( cacheSize );
		((CacheMap)invalidUserMap).setSize( cacheSize );

		this.initialized = true;
		LOGGER.finer("Cache settings updated enabled: " + this.enabled + " ttl: " + this.ttlSec + " size: " + cacheSize);
	}

	public Collection<String> getRolesForUser(String username) {
		synchronized (rolesByUserCache) {
			CacheEntry<Collection<String>> rolesEntry = rolesByUserCache.get(username);
			if (rolesEntry != null && rolesEntry.isValid()) {
				return rolesEntry.getValue();
			}
		}
		return null;
	}

	public Collection<String> getRoles() {
		if (roleCache != null && roleCache.isValid()) {
			return roleCache.getValue();
		}
		return null;
	}

	public void setRoles(Collection<String> roles) {
		roleCache = new CacheEntry<>( ttlSec, roles );
	}

	public void setRolesForUser(String username, String[] roles) {
		if (roles != null) {
			Collection<String> roleCollection = Arrays.asList(roles);
			CacheEntry<Collection<String>> cacheEntry = new CacheEntry<>( ttlSec, roleCollection );
			synchronized (rolesByUserCache) {
				rolesByUserCache.put(username, cacheEntry);
			}
		}
	}

	public boolean isInvalidUser(String username) {
		synchronized (invalidUserMap) {
			CacheEntry<Boolean> invalidUserData = invalidUserMap.get( username );
			if (invalidUserData != null && invalidUserData.isValid()) {
				LOGGER.fine(username + " is invalid: " + invalidUserData.getValue());
				return invalidUserData.getValue();
			}
		}
		return false;
	}

	public void addInvalidUser(String username) {
		synchronized (invalidUserMap) {
			LOGGER.fine( "Caching invalid user: " + username );
			CacheEntry<Boolean> invalidUserData = new CacheEntry<>( ttlSec, true );
			invalidUserMap.put( username, invalidUserData );
		}
	}

	public void setSystemToken(String token, long tokenExpiration) {
		synchronized (TOKEN_LOCK) {
			this.token = token;
			this.tokenExpiration = System.currentTimeMillis() + tokenExpiration - 500; //include 500ms buffer
		}
	}

	public String getSystemToken() {
		synchronized (TOKEN_LOCK) {
			if ( token != null && System.currentTimeMillis() < tokenExpiration ) {
				return token;
			}
			return null;
		}
	}

	private static class CacheEntry<T> {
		private final long expires;
		private final T value;

		public CacheEntry(int ttlSeconds, T value) {
			this.expires = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttlSeconds);
			this.value = value;
		}

		public T getValue() {
			return value;
		}

		public boolean isValid() {
			return System.currentTimeMillis() < expires;
		}
	}

	/**
	 * While we could use Guava's CacheBuilder the method signature changes make using it problematic.
	 * Safer to roll our own and ensure compatibility across as wide a range of Jenkins versions as possible.
	 *
	 * @param <K> Key type
	 * @param <V> Cache entry type
	 */
	private static class CacheMap<K, V> extends LinkedHashMap<K, CacheEntry<V>> {

		private static final long serialVersionUID = 1L;
		private int cacheSize;

		public CacheMap(int cacheSize) {
			super(cacheSize + 1);
			this.cacheSize = cacheSize;
		}

		public void setSize(int cacheSize) {
			this.cacheSize = cacheSize;
		}

		@Override
		protected boolean removeEldestEntry( Map.Entry<K, CacheEntry<V>> eldest) {
			return size() > cacheSize || eldest.getValue() == null || !eldest.getValue().isValid();
		}
	}
}
