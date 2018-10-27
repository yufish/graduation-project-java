package nl.inl.blacklab.server.auth;

import lombok.extern.slf4j.Slf4j;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.SearchManager;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Authentication system using servlet request attributes for logged-in user id.
 *
 * Can be used, for example, with Shibboleth authentication.
 */
@Slf4j
public class AuthRequestAttribute {

	private String attributeName = null;

	public AuthRequestAttribute(Map<String, Object> parameters) {
		Object parName = parameters.get("attributeName");
		if (parName == null) {
			log.error("authSystem.attributeName parameter missing in blacklab-server.json");
		} else {
			this.attributeName = parName.toString();
		}
	}

	public AuthRequestAttribute(String attributeName) {
		this.attributeName = attributeName;
	}

	public User determineCurrentUser(BlackLabServer servlet,
			HttpServletRequest request) {
		String sessionId = request.getSession().getId();
		if (attributeName == null) {
			// (not configured correctly)
			log.warn("Cannot determine current user; missing authSystem.attributeName parameter in blacklab-server.json");
			return User.anonymous(sessionId);
		}

		// See if there's a logged-in user or not
		String userId = getUserId(servlet, request);

		// Return the appropriate User object
		if (userId == null || userId.length() == 0) {
			return User.anonymous(sessionId);
		}
		return User.loggedIn(userId, sessionId);
	}

	protected String getUserId(BlackLabServer servlet, HttpServletRequest request) {

		String userId = null;

		// Overridden in URL?
		SearchManager searchMan = ((BlackLabServer)servlet).getSearchManager();
		if (searchMan.config().overrideUserId(request.getRemoteAddr()) && request.getParameter("userid") != null) {
			userId = request.getParameter("userid");
		}

		if (userId == null) {
			Object attribute = request.getAttribute(attributeName);
			if (attribute != null) {
                userId = attribute.toString();
            }
		}

		return userId;
	}

}
