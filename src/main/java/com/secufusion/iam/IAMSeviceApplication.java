package com.secufusion.iam;

import com.secufusion.iam.service.InitializerExecutor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@RequiredArgsConstructor
public class IAMSeviceApplication {

    private final InitializerExecutor initializerExecutor;


    public static void main(String[] args) {
		SpringApplication.run(IAMSeviceApplication.class, args);
	}

    @PostConstruct
    public void initDefaultTenant() {
        initializerExecutor.runInitialization();
    }

}
