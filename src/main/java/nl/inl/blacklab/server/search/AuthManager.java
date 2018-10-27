package nl.inl.blacklab.server.search;

import lombok.extern.slf4j.Slf4j;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.jobs.User;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;

@Slf4j
public class AuthManager {
	/** The authentication object, giving information about the currently logged-in user
        (or at least a session id) */
	private Object authObj = null;

	/** The method to invoke for determining the current user. */
	private Method authMethodDetermineCurrentUser = null;

	public AuthManager(String authClass, Map<String, Object> authParam) {
		if (authClass.length() > 0) {
			try {
				if (!authClass.contains(".")) {
					// Allows us to abbreviate the built-in auth classes
					authClass = "nl.inl.blacklab.server.auth." + authClass;
				}
				Class<?> cl = Class.forName(authClass);
				authObj = cl.getConstructor(Map.class).newInstance(authParam);
				authMethodDetermineCurrentUser = cl.getMethod("determineCurrentUser", BlackLabServer.class, HttpServletRequest.class);
			} catch (Exception e) {
				throw new RuntimeException("Error instantiating auth system: " + authClass, e);
			}
			log.info("Auth system initialized: " + authClass);
		} else {
			log.info("No auth system configured");
		}
	}

	public Method getAuthMethodDetermineCurrentUser() {
		return authMethodDetermineCurrentUser;
	}

	public User determineCurrentUser(BlackLabServer servlet, HttpServletRequest request) {
		// If no auth system is configured, all users are anonymous
		if (authObj == null) {
			User user = User.anonymous(request.getSession().getId());
			return user;
		}

		// Let auth system determine the current user.
		try {
			User user = (User)authMethodDetermineCurrentUser.invoke(authObj, servlet, request);
			return user;
		} catch (Exception e) {
			throw new RuntimeException("Error determining current user", e);
		}
	}

}