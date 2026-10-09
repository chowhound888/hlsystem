package com.hlsystem.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfig {
  @Bean
  Jackson2ObjectMapperBuilderCustomizer decimalSerialization() {
    return builder -> {
      var module = new SimpleModule();
      module.addSerializer(Long.class, ToStringSerializer.instance);
      module.addSerializer(Long.TYPE, ToStringSerializer.instance);
      module.addSerializer(BigDecimal.class, ToStringSerializer.instance);
      builder.modulesToInstall(module);
    };
  }
}
