package com.degel.file.service;

import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.internal.StringUtil;
import com.degel.file.properties.MinioProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MinioProperties minioProperties;

    @PostConstruct
    public void initBuckets() {
        ensureBucketExists(minioProperties.getPublicBucket());
        ensureBucketExists(minioProperties.getPrivateBucket());
        ensurePublicBucketPolicy();
    }

    /**
     * 上传文件。
     * 公开 bucket 返回永久访问 URL；私有 bucket 返回 objectName。
     */
    public String upload(MultipartFile file, String bucketType) throws IOException {
        String bucket = resolveBucket(bucketType);
        String objectName = UUID.randomUUID() + "_" + file.getOriginalFilename();

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectName)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromBytes(file.getBytes()));

        if ("public".equals(bucketType)) {
            return minioProperties.getEndpoint() + "/" + bucket + "/" + objectName;
        }
        return objectName;
    }

    /**
     * 删除文件。
     */
    public void delete(String bucketType, String objectName) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(resolveBucket(bucketType))
                .key(objectName)
                .build());
    }

    /**
     * 列举 bucket 内文件（支持前缀过滤）。
     */
    public List<String> list(String bucketType, String prefix) {
        ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                .bucket(resolveBucket(bucketType));
        if (StringUtil.isNullOrEmpty(prefix)) {
            builder.prefix(prefix);
        }
        return s3Client.listObjectsV2(builder.build())
                .contents()
                .stream()
                .map(S3Object::key)
                .collect(Collectors.toList());
    }

    /**
     * 生成预签名 URL，支持 inline（预览）和 attachment（下载）。
     */
    public String presign(String bucketType, String objectName, int expires, String disposition) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(resolveBucket(bucketType))
                .key(objectName)
                .responseContentDisposition(
                        "attachment".equals(disposition)
                                ? "attachment; filename=\"" + objectName + "\""
                                : "inline")
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expires))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private String resolveBucket(String bucketType) {
        return "private".equals(bucketType)
                ? minioProperties.getPrivateBucket()
                : minioProperties.getPublicBucket();
    }

    private void ensureBucketExists(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception ex) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    private void ensurePublicBucketPolicy() {
        String bucket = minioProperties.getPublicBucket();
        String policy = "{"
                + "\"Version\":\"2012-10-17\","
                + "\"Statement\":[{"
                + "\"Effect\":\"Allow\","
                + "\"Principal\":\"*\","
                + "\"Action\":[\"s3:GetObject\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]"
                + "}]"
                + "}";

        s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                .bucket(bucket)
                .policy(policy)
                .build());
    }
}
