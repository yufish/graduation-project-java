package com.hugailei.graduation.corpus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication
@EntityScan({"com.hugailei.graduation.corpus.domain"})
//禁用spring自动配置数据库
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
public class CorpusApplication {

    public static void main(String[] args) {
        SpringApplication.run(CorpusApplication.class, args);
    }
}
