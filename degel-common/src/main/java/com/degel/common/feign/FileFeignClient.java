package com.degel.common.feign;

import com.degel.common.core.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@FeignClient(name = "degel-file", contextId = "fileFeignClient")
public interface FileFeignClient {

    @PostMapping(value = "/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    R<String> upload(@RequestPart("file") MultipartFile file,
                     @RequestParam(value = "bucket", defaultValue = "public") String bucket);

    @DeleteMapping("/file")
    R<Void> delete(@RequestParam("bucket") String bucket,
                   @RequestParam("objectName") String objectName);

    @GetMapping("/file/list")
    R<List<String>> list(@RequestParam("bucket") String bucket,
                         @RequestParam(value = "prefix", required = false) String prefix);

    @GetMapping("/file/presign")
    R<String> presign(@RequestParam("bucket") String bucket,
                      @RequestParam("objectName") String objectName,
                      @RequestParam(value = "expires", defaultValue = "3600") int expires,
                      @RequestParam(value = "disposition", defaultValue = "inline") String disposition);
}
