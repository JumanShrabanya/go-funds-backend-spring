package com.js.gofunds_backend.config;

import com.js.gofunds_backend.funds.service.FundClassifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FundsConfig {

	@Bean
	public FundClassifier fundClassifier() {
		return new FundClassifier();
	}
}
