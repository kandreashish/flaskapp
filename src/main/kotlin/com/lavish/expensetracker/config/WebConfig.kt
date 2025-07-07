package com.lavish.expensetracker.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.KotlinSerializationJsonHttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val kotlinSerializationJsonHttpMessageConverter: KotlinSerializationJsonHttpMessageConverter
) : WebMvcConfigurer {

    override fun configureMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        // Add our custom Kotlinx Serialization converter at the beginning
        // This ensures it takes precedence over the default Jackson converter
        converters.add(0, kotlinSerializationJsonHttpMessageConverter)
    }
}
