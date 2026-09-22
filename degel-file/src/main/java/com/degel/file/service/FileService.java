package com.degel.file.service;

import com.degel.common.core.exception.BusinessException;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileService {

    /**
     * 上传允许的扩展名（小写）。html/svg 等可在浏览器执行脚本的类型一律拒绝，
     * 客户端声明的 Content-Type 一律不采信（H4 修复，防公开桶同源存储型 XSS）。
     */
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "pdf"));

    /** 扩展名 → 服务端认定的 Content-Type；未命中按 application/octet-stream + attachment 处理 */
    private static final Map<String, String> EXT_CONTENT_TYPES = new HashMap<>();

    /** 可 inline 展示的扩展名（图片；pdf 等其余类型强制 attachment 下载） */
    private static final Set<String> INLINE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "gif", "webp", "bmp"));

    static {
        EXT_CONTENT_TYPES.put("png", "image/png");
        EXT_CONTENT_TYPES.put("jpg", "image/jpeg");
        EXT_CONTENT_TYPES.put("jpeg", "image/jpeg");
        EXT_CONTENT_TYPES.put("gif", "image/gif");
        EXT_CONTENT_TYPES.put("webp", "image/webp");
        EXT_CONTENT_TYPES.put("bmp", "image/bmp");
        EXT_CONTENT_TYPES.put("pdf", "application/pdf");
    }

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
        // H4 修复：剥掉文件名中的路径成分 + 扩展名白名单；存储的 Content-Type 由服务端按扩展名推导
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String extension = extensionOf(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("不支持的文件类型："
                    + (extension.isEmpty() ? "缺少扩展名" : extension)
                    + "，仅允许图片（png/jpg/jpeg/gif/webp/bmp）与 pdf");
        }
        String objectName = UUID.randomUUID() + "_" + fileName;

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectName)
                        .contentType(EXT_CONTENT_TYPES.get(extension))
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
        // H4 修复：Content-Type 由服务端按扩展名认定（不信任对象存储的元数据——历史上
        // 上传时采信过客户端声明，存量对象可能带 text/html 等危险类型）；非图片强制
        // attachment；nosniff 阻止浏览器嗅探把响应还原成可执行文档
        String extension = extensionOf(objectName);
        response.setContentType(EXT_CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"));
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (!INLINE_EXTENSIONS.contains(extension)) {
            response.setHeader("Content-Disposition", "attachment; filename=\"" + objectName + "\"");
        }
        try (software.amazon.awssdk.core.ResponseInputStream<GetObjectResponse> in =
                     s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectName).build())) {
            GetObjectResponse meta = in.response();
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
        // 顺带修复（2026-09-20 扫描低危项）：原条件写反——为空才设前缀，导致 /file/list 永远全量枚举
        if (prefix != null && !prefix.isEmpty()) {
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
        // M6 修复：有效期服务端钳制 1~3600 秒——原样放行用户传的 expires 可对私有桶对象
        // 签出数十年有效的公开直链，"私有桶需 token"的约束被绕穿
        int clamped = Math.max(1, Math.min(expires, 3600));
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(resolveBucket(bucketType))
                .key(objectName)
                .responseContentDisposition(
                        "attachment".equals(disposition)
                                ? "attachment; filename=\"" + objectName + "\""
                                : "inline")
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(clamped))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /** 剥掉文件名里的路径成分（'/'、'\'），拒绝空名/点号名 */
    private static String sanitizeFileName(String original) {
        if (original == null || original.isEmpty()) {
            throw new BusinessException("文件名不能为空");
        }
        String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            throw new BusinessException("非法文件名");
        }
        return name;
    }

    /** 取小写扩展名；无扩展名或以点结尾返回空串 */
    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
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
