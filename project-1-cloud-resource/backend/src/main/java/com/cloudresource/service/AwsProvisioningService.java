package com.cloudresource.service;

import com.cloudresource.domain.ResourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AwsProvisioningService {

    private final Ec2Client ec2Client;
    private final S3Client s3Client;

    @Value("${aws.region}")
    private String region;

    // Amazon Linux 2023 AMI per region
    private static final Map<String, String> AMI_BY_REGION = Map.of(
            "us-east-1",      "ami-0c02fb55956c7d316",
            "us-west-2",      "ami-0ceecbb0f30a902a6",
            "eu-west-1",      "ami-0d71ea30463e0ff49",
            "eu-central-1",   "ami-0a261c0e5f51090b1",
            "ap-southeast-1", "ami-0df7a207adb9748c7",
            "ap-northeast-1", "ami-0d52744d6551d851e"
    );
    private static final String DEFAULT_AMI = "ami-0c02fb55956c7d316";
    private static final String DEFAULT_INSTANCE_TYPE = "t2.micro";

    public String provision(ResourceType type, String name, String resourceRegion) {
        return switch (type) {
            case EC2 -> launchEc2(name, resourceRegion);
            case S3 -> createS3Bucket(name, resourceRegion);
        };
    }

    public void stop(ResourceType type, String awsResourceId, String resourceRegion) {
        if (type == ResourceType.EC2) {
            ec2ClientForRegion(resourceRegion).stopInstances(StopInstancesRequest.builder()
                    .instanceIds(awsResourceId)
                    .build());
            log.info("Stopped EC2 instance: {} in {}", awsResourceId, resourceRegion);
        }
    }

    public void start(ResourceType type, String awsResourceId, String resourceRegion) {
        if (type == ResourceType.EC2) {
            ec2ClientForRegion(resourceRegion).startInstances(StartInstancesRequest.builder()
                    .instanceIds(awsResourceId)
                    .build());
            log.info("Started EC2 instance: {} in {}", awsResourceId, resourceRegion);
        }
    }

    public void terminate(ResourceType type, String awsResourceId, String resourceRegion) {
        switch (type) {
            case EC2 -> {
                ec2ClientForRegion(resourceRegion).terminateInstances(TerminateInstancesRequest.builder()
                        .instanceIds(awsResourceId)
                        .build());
                log.info("Terminated EC2 instance: {} in {}", awsResourceId, resourceRegion);
            }
            case S3 -> {
                deleteS3Bucket(awsResourceId, resourceRegion);
                log.info("Deleted S3 bucket: {}", awsResourceId);
            }
        }
    }

    private Ec2Client ec2ClientForRegion(String resourceRegion) {
        return Ec2Client.builder()
                .region(Region.of(resourceRegion))
                .credentialsProvider(ec2Client.serviceClientConfiguration().credentialsProvider())
                .build();
    }

    private String launchEc2(String name, String resourceRegion) {
        String ami = AMI_BY_REGION.getOrDefault(resourceRegion, DEFAULT_AMI);
        Ec2Client regionalClient = Ec2Client.builder()
                .region(Region.of(resourceRegion))
                .credentialsProvider(ec2Client.serviceClientConfiguration().credentialsProvider())
                .build();
        RunInstancesResponse response = regionalClient.runInstances(RunInstancesRequest.builder()
                .imageId(ami)
                .instanceType(InstanceType.T2_MICRO)
                .minCount(1)
                .maxCount(1)
                .tagSpecifications(TagSpecification.builder()
                        .resourceType(software.amazon.awssdk.services.ec2.model.ResourceType.INSTANCE)
                        .tags(software.amazon.awssdk.services.ec2.model.Tag.builder().key("Name").value(name).build(),
                              software.amazon.awssdk.services.ec2.model.Tag.builder().key("ManagedBy").value("CloudOps").build())
                        .build())
                .build());
        String instanceId = response.instances().get(0).instanceId();
        log.info("Launched EC2 instance: {} in {} for resource: {}", instanceId, resourceRegion, name);
        return instanceId;
    }

    private String createS3Bucket(String name, String resourceRegion) {
        String bucketName = "cloudops-" + name.toLowerCase().replaceAll("[^a-z0-9-]", "-")
                + "-" + System.currentTimeMillis();

        S3Client regionalS3 = S3Client.builder()
                .region(Region.of(resourceRegion))
                .credentialsProvider(s3Client.serviceClientConfiguration().credentialsProvider())
                .build();

        if (resourceRegion.equals("us-east-1")) {
            regionalS3.createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
        } else {
            regionalS3.createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .createBucketConfiguration(CreateBucketConfiguration.builder()
                            .locationConstraint(BucketLocationConstraint.fromValue(resourceRegion))
                            .build())
                    .build());
        }

        regionalS3.putPublicAccessBlock(PutPublicAccessBlockRequest.builder()
                .bucket(bucketName)
                .publicAccessBlockConfiguration(PublicAccessBlockConfiguration.builder()
                        .blockPublicAcls(true)
                        .blockPublicPolicy(true)
                        .ignorePublicAcls(true)
                        .restrictPublicBuckets(true)
                        .build())
                .build());

        log.info("Created S3 bucket: {} in {}", bucketName, resourceRegion);
        return bucketName;
    }

    private void deleteS3Bucket(String bucketName, String resourceRegion) {
        S3Client regionalS3 = S3Client.builder()
                .region(Region.of(resourceRegion))
                .credentialsProvider(s3Client.serviceClientConfiguration().credentialsProvider())
                .build();
        try {
            ListObjectsV2Response objects = regionalS3.listObjectsV2(
                    ListObjectsV2Request.builder().bucket(bucketName).build());
            if (!objects.contents().isEmpty()) {
                regionalS3.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucketName)
                        .delete(Delete.builder()
                                .objects(objects.contents().stream()
                                        .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                                        .toList())
                                .build())
                        .build());
            }
            regionalS3.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());
        } catch (Exception e) {
            log.error("Failed to delete S3 bucket {}: {}", bucketName, e.getMessage());
            throw e;
        }
    }
}
