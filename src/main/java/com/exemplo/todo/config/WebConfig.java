package com.exemplo.todo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	// endereço sem ".html" para a política de privacidade (é o link que aparece na tela de consentimento do Google)
	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/privacidade").setViewName("forward:/privacidade.html");
	}

}
