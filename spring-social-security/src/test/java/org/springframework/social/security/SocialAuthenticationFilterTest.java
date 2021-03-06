/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.social.security;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.NullRememberMeServices;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.ConnectionData;
import org.springframework.social.connect.ConnectionFactory;
import org.springframework.social.connect.ConnectionKey;
import org.springframework.social.connect.ConnectionRepository;
import org.springframework.social.connect.UsersConnectionRepository;
import org.springframework.social.security.provider.SocialAuthenticationService;
import org.springframework.social.security.provider.SocialAuthenticationService.ConnectionCardinality;
import org.springframework.social.security.test.DummyConnection;
import org.springframework.social.security.test.MockConnectionFactory;

public class SocialAuthenticationFilterTest {

	@Before
	@After
	public void clean() {
		SecurityContextHolder.getContext().setAuthentication(null);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testExplicitAuth() throws Exception {

		FilterTestEnv env = new FilterTestEnv("GET", "/auth");
		env.filter.setFilterProcessesUrl(env.req.getRequestURI());
		env.filter.setPostLoginUrl("/success");
		
		ConnectionFactory<Object> factory = mock(MockConnectionFactory.class);
		when(factory.getProviderId()).thenReturn("mock");
		env.req.setRequestURI(env.req.getRequestURI() + "/" + factory.getProviderId());

		SocialAuthenticationService<Object> authService = mock(SocialAuthenticationService.class);
		when(authService.getConnectionCardinality()).thenReturn(ConnectionCardinality.ONE_TO_ONE);
		when(authService.getConnectionFactory()).thenReturn(factory);
		when(authService.getAuthToken(env.req, env.res)).thenReturn(env.auth);
		env.addAuthService(authService);

		when(env.filter.getAuthManager().authenticate(env.auth)).thenReturn(env.authSuccess);

		assertNull(SecurityContextHolder.getContext().getAuthentication());

		env.doFilter();

		assertNotNull(SecurityContextHolder.getContext().getAuthentication());
		
		assertEquals("/success", env.res.getRedirectedUrl());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void addConnection() {
		UsersConnectionRepository usersConnectionRepository = mock(UsersConnectionRepository.class);
		SocialAuthenticationFilter filter = new SocialAuthenticationFilter(null, null, usersConnectionRepository, null);
		
		SocialAuthenticationService<Object> authService = mock(SocialAuthenticationService.class);
		ConnectionRepository connectionRepository = mock(ConnectionRepository.class);
		ConnectionFactory<Object> connectionFactory = mock(MockConnectionFactory.class);
		
		MockHttpServletRequest request = new MockHttpServletRequest();
		ConnectionData data = new ConnectionData("dummyprovider", "1234", null, null, null, null, null, null, null);
		String userId = "joe";
		
		DummyConnection<Object> connection = DummyConnection.dummy(data.getProviderId(), userId);
		
		when(usersConnectionRepository.findUserIdsConnectedTo(data.getProviderId(), set(data.getProviderUserId()))).thenReturn(empty(String.class));
		when(usersConnectionRepository.createConnectionRepository(userId)).thenReturn(connectionRepository);
		
		when(authService.getConnectionCardinality()).thenReturn(ConnectionCardinality.ONE_TO_ONE);
		when(authService.getConnectionFactory()).thenReturn(connectionFactory);
		when(authService.getConnectionAddedRedirectUrl(request, connection)).thenReturn("/redirect");
		
		when(connectionFactory.createConnection(data)).thenReturn(connection);
		
		Connection<?> addedConnection = filter.addConnection(authService, userId, data);
		assertNotNull(addedConnection);
		assertSame(connection, addedConnection);
		
		verify(connectionRepository).addConnection(connection);
	}
	
	@Test
	public void addSignInAttempt() {
		MockHttpSession session = new MockHttpSession();
		assertFalse(addSignInAttempt(session, data("A", "a")));
		assertTrue(addSignInAttempt(session, data("A", "a")));
		assertFalse(addSignInAttempt(session, data("A", "b")));
		assertFalse(addSignInAttempt(session, data("B", "a")));
		assertEquals(3, getSignInAttempts(session).size());
		removeSignInAttempt(session, getSignInAttempts(session).get(0));
		assertEquals(2, getSignInAttempts(session).size());
		clearSignInAttempts(session);
		assertEquals(0, getSignInAttempts(session).size());
	}
	
	private boolean removeSignInAttempt(HttpSession session, ConnectionKey key) {
		return session != null ? SignInAttempts.remove(session, key) : false;
	}
	
	private boolean removeSignInAttempt(HttpSession session, ConnectionData data) {
		return removeSignInAttempt(session, SignInAttempts.key(data));
	}
	
	private List<ConnectionData> getSignInAttempts(HttpSession session) {
		if (session == null) {
			return new ArrayList<ConnectionData>(0);
		}
		return new ArrayList<ConnectionData>(SignInAttempts.get(session));
	}

	private void clearSignInAttempts(HttpSession session) {
		if (session != null) {
			SignInAttempts.clear(session);
		}
	}

	private boolean addSignInAttempt(HttpSession session, ConnectionData data) {
		return session == null || data == null ? null : SignInAttempts.add(session, data);
	}

	private static <T> Set<T> empty(Class<T> cls) {
		return Collections.emptySet();
	}
	
	private static <T> Set<T> set(T ... o) {
		return Collections.unmodifiableSet(new HashSet<T>(Arrays.asList(o)));
	}

	private static ConnectionData data(String providerId, String providerUserId) {
		return new ConnectionData(providerId, providerUserId, null, null, null, null, null, null, null);
	}
	
	private static class FilterTestEnv {
		private final SocialAuthenticationFilter filter;
		private final MockServletContext context;
		private final MockHttpServletRequest req;
		private final MockHttpServletResponse res;
		private final MockFilterChain chain;
		private final MockFilterConfig config = new MockFilterConfig();
		private final SocialAuthenticationToken auth;
		private final SocialAuthenticationToken authSuccess;

		private FilterTestEnv(String method, String requestURI) {
			context = new MockServletContext();
			req = new MockHttpServletRequest(context, method, requestURI);
			res = new MockHttpServletResponse();
			chain = new MockFilterChain();

			filter = new SocialAuthenticationFilter(mock(AuthenticationManager.class), mock(UserIdExtractor.class), mock(UsersConnectionRepository.class), new SocialAuthenticationServiceRegistry());
			filter.setServletContext(context);
			filter.setRememberMeServices(new NullRememberMeServices());
			
			ConnectionRepository repo = mock(ConnectionRepository.class);
			when(filter.getUsersConnectionRepository().createConnectionRepository(Mockito.anyString())).thenReturn(repo);
			
			auth = new SocialAuthenticationToken(DummyConnection.dummy("provider", "user").createData(), null);

			Collection<? extends GrantedAuthority> authorities = Collections.emptyList();
			User user = new SocialUser("foo", "bar", authorities);
			authSuccess = new SocialAuthenticationToken("mock", user, null, authorities);
		}

		private void addAuthService(SocialAuthenticationService<?> authenticationService) {
			((SocialAuthenticationServiceRegistry)filter.getAuthServiceLocator()).addAuthenticationService(authenticationService);
		}
		
		private void doFilter() throws Exception {

			filter.init(config);
			filter.doFilter(req, res, chain);
			filter.destroy();
		}
	}
}
