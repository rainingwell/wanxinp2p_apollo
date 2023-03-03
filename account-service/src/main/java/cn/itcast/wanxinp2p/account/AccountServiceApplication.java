package cn.itcast.wanxinp2p.account;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages =
		{"org.dromara.hmily","cn.itcast.wanxinp2p.account"})
@MapperScan("cn.itcast.wanxinp2p.account.mapper")
public class AccountServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccountServiceApplication.class, args);
	}

}
