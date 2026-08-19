package ru.yandex.practicum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class  AccountsApplication  {
    public static void main(String[] args) {
        SpringApplication.run(AccountsApplication.class, args);
    }
}