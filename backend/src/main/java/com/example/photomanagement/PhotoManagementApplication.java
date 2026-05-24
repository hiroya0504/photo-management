package com.example.photomanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PhotoManagementApplication {

  public static void main(String[] args) {
    SpringApplication.run(PhotoManagementApplication.class, args);
  }
}
