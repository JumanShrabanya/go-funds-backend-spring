package com.js.gofunds_backend.guest.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

	@Test
	void usesTheSocketAddressByDefault() {
		ClientIpResolver resolver = new ClientIpResolver(false);
		MockHttpServletRequest request = request();
		request.addHeader("X-Forwarded-For", "198.51.100.4");

		assertEquals("203.0.113.7", resolver.resolve(request));
	}

	@Test
	void usesTheFirstForwardedAddressWhenTrusted() {
		ClientIpResolver resolver = new ClientIpResolver(true);
		MockHttpServletRequest request = request();
		request.addHeader("X-Forwarded-For", "198.51.100.4, 10.0.0.1");

		assertEquals("198.51.100.4", resolver.resolve(request));
	}

	@Test
	void fallsBackToTheSocketAddressWhenTheHeaderIsMissing() {
		ClientIpResolver resolver = new ClientIpResolver(true);

		assertEquals("203.0.113.7", resolver.resolve(request()));
	}

	private static MockHttpServletRequest request() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.7");
		return request;
	}
}
