package com.hlsystem.system;

import com.hlsystem.HlsystemApplication;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;

public class PopulateDemoData {
  public static void main(String[] args) {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    try(var context=SpringApplication.run(HlsystemApplication.class,
        "--server.port=0","--app.demo.enabled=true","--app.demo.expanded-enabled=true")) {
      System.out.println("Expanded teaching data is ready. Existing transactions and passwords retained.");
    }
  }
}
