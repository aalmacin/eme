package com.raidrin.eme.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${image.output.directory:./generated_images}")
    private String imageOutputDirectory;

    @Value("${audio.output.directory:./generated_audio}")
    private String audioOutputDirectory;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve generated images
        registry.addResourceHandler("/generated_images/**")
                .addResourceLocations("file:" + imageOutputDirectory + "/");

        // Serve generated audio (optional, for future use)
        registry.addResourceHandler("/generated_audio/**")
                .addResourceLocations("file:" + audioOutputDirectory + "/");
    }
}
