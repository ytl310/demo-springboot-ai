package com.example.demo.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.RedisClient;

import java.time.Duration;

/**
 * @author ytl
 * @date 2026/7/1 18:16
 * @description TODO
 */
@Configuration
public class RedisConfig {


    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    @Value("${spring.redis.database:0}")
    private Integer redisDatabase;


//    @Bean
//    public RedisClient redisClient() {
//        RedisURI redisUri = RedisURI.builder()
//                .withHost(redisHost)
//                .withPort(redisPort)
//                .withPassword(redisPassword) // 设置密码
//                .withDatabase(0)
//                .build();
//        return RedisClient.create(redisUri.toURI());
//    }

//    @Bean
//    public RedisClient redisClient() throws URISyntaxException {
//        // 对密码进行 URL 编码（处理特殊字符）
//        String encodedPassword = URLEncoder.encode(redisPassword, StandardCharsets.UTF_8);
//        String uri = String.format("redis://:%s@%s:%d/%d",
//                encodedPassword, redisHost, redisPort, 0);
//
//        return RedisClient.create(new URI(uri));
//    }

    /**
     * @author ytl
     * @date 2026/7/5 0:24
     * @param []
     * @return redis.clients.jedis.RedisClient
     * @description RedisClient.builder().build() 使用了懒加载（Lazy Init）机制，
     * 不会在对象创建时建立真正的网络连接,也就是说可以先配置bean，
     * 只要不在配置类中引用RedisClient，启动就不报错，使用的时候如果没启动redis才会报错，这就是懒加载的好处
     **/
    @Bean
    public RedisClient redisClient() {
        // 使用Jedis 7.2.0+的新API创建客户端
        // 1. 创建 HostAndPort 对象来指定地址
        HostAndPort hostAndPort = new HostAndPort(redisHost, redisPort);

        // 2. 创建客户端配置，设置密码、超时等
        DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
                // 设置密码
                .password(redisPassword)
                .database(redisDatabase)
                .connectionTimeoutMillis(2000)
                .socketTimeoutMillis(2000)
                .build();

        return  RedisClient.builder()
                .hostAndPort(hostAndPort)
                .clientConfig(config)
                .build();
    }

    /**
     * @author ytl
     * @date 2026/7/1 18:19
     * @param []
     * @return org.springframework.data.redis.connection.jedis.JedisConnectionFactory
     * @description RetrievalAugmentationAdvisor需要依赖JedisConnectionFactory
     **/
    @Bean
    public JedisConnectionFactory jedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setPassword(redisPassword);
        config.setDatabase(redisDatabase);

        JedisClientConfiguration jedisClientConfig = JedisClientConfiguration.builder()
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(2))
                .usePooling()
                .build();

        return new JedisConnectionFactory(config, jedisClientConfig);
    }

    @Bean
//    @ConditionalOnProperty(name = "spring.redis.host")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + redisHost + ":" + redisPort;
        config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisDatabase)
                .setConnectionPoolSize(10)
                .setConnectionMinimumIdleSize(2);

        // Only set password if it's not null or empty
        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            config.useSingleServer().setPassword(redisPassword);
        }

        return Redisson.create(config);
    }
}
