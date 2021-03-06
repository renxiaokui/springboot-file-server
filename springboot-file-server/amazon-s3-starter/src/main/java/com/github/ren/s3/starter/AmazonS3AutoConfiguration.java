package com.github.ren.s3.starter;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Description
 * @Author ren
 * @Since 1.0
 */
@Configuration
@ConditionalOnClass(AmazonS3.class)
@EnableConfigurationProperties(S3Properties.class)
public class AmazonS3AutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = Constants.S3Prefix, name = "enabled", matchIfMissing = true)
    public AmazonS3 amazonS3(S3Properties s3Properties) {
        String accessKey = s3Properties.getAccessKey();
        String secretKey = s3Properties.getSecretKey();
        String endpoint = s3Properties.getEndpoint();
        String region = s3Properties.getRegion();
        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        AwsClientBuilder.EndpointConfiguration endpointConfiguration =
                new AwsClientBuilder.
                        EndpointConfiguration(endpoint, region);

        return AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withEndpointConfiguration(endpointConfiguration)
                .build();
    }

}
