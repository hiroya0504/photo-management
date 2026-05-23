package com.example.photomanagement;

import org.springframework.boot.SpringApplication;

public class TestPhotoManagementApplication {

  public static void main(String[] args) {
    SpringApplication.from(PhotoManagementApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
