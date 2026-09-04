package com.degel.file.controller;

import com.degel.common.core.R;
import com.degel.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public R<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bucket", defaultValue = "public") String bucket) throws Exception {
        return R.ok(fileService.upload(file, bucket));
    }

    @DeleteMapping
    public R<Void> delete(
            @RequestParam("bucket") String bucket,
            @RequestParam("objectName") String objectName) {
        fileService.delete(bucket, objectName);
        return R.ok();
    }

    @GetMapping("/list")
    public R<List<String>> list(
            @RequestParam("bucket") String bucket,
            @RequestParam(value = "prefix", required = false) String prefix) {
        return R.ok(fileService.list(bucket, prefix));
    }

    @GetMapping("/presign")
    public R<String> presign(
            @RequestParam("bucket") String bucket,
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "expires", defaultValue = "3600") int expires,
            @RequestParam(value = "disposition", defaultValue = "inline") String disposition) {
        return R.ok(fileService.presign(bucket, objectName, expires, disposition));
    }

    /**
     * 图片/文件展示：GET /file/view/{bucket}/{objectName}
     * 前端用相对路径（经网关），库里的 objectKey 不含 host，环境无关。
     */
    @GetMapping("/view/{bucket}/{objectName:.+}")
    public void view(
            @PathVariable String bucket,
            @PathVariable String objectName,
            javax.servlet.http.HttpServletResponse response) throws java.io.IOException {
        fileService.writeTo(bucket, objectName, response);
    }
}
