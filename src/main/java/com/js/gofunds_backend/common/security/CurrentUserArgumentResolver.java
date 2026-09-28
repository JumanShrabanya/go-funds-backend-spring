package com.js.gofunds_backend.common.security;

import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.domain.entity.User;
import com.js.gofunds_backend.domain.repository.UserRepository;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

	private final UserRepository userRepository;

	public CurrentUserArgumentResolver(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(CurrentUser.class)
				&& User.class.isAssignableFrom(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
								  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
		}
		return userRepository.findById(principal.id())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
	}
}