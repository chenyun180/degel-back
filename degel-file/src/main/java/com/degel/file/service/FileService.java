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
     * 统一返回 objectKey（bucket/objectName），不含 host——环境差异由配置承担，库里不落绝对 URL。
     * 展示时用 GET /file/view/{objectKey}（走网关相对路径），或由调用方按配置拼公网地址。
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

        return bucket + "/" + objectName;
    }

    /**
     * 把对象流式写入 HttpServletResponse（GET /file/view/{bucket}/{objectName}）。
     * 前端用相对路径经网关访问，host 完全不出现在任何 URL 里。
     */
    public void writeTo(String bucket, String objectName, javax.servlet.http.HttpServletResponse response) throws IOException {
        // 只允许访问配置中声明的两个 bucket，防止任意 bucket 探测
        if (!bucket.equals(minioProperties.getPublicBucket()) && !bucket.equals(minioProperties.getPrivateBucket())) {
            response.sendError(javax.servlet.http.HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try (software.amazon.awssdk.core.ResponseInputStream<GetObjectResponse> in =
                     s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectName).build())) {
            GetObjectResponse meta = in.response();
            if (meta.contentType() != null) {
                response.setContentType(meta.contentType());
            }
            if (meta.contentLength() != null) {
                response.setContentLength(meta.contentLength().intValue());
            }
            byte[] buffer = new byte[8192];
            int len;
            java.io.OutputStream out = response.getOutputStream();
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        } catch (NoSuchKeyException e) {
            response.sendError(javax.servlet.http.HttpServletResponse.SC_NOT_FOUND);
        }
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
