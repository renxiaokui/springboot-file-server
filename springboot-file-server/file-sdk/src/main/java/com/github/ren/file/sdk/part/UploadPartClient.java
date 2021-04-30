package com.github.ren.file.sdk.part;

import java.util.List;

/**
 * @Description 分片上传处理客户端
 * @Author ren
 * @Since 1.0
 */
public interface UploadPartClient extends PartClient {

    /**
     * 获取上传分片列表
     *
     * @param uploadId 分片标识 本地或者redis 可以用md5值 oss取initUpload()的返回值
     * @return
     */
    List<UploadPart> listUploadParts(String uploadId);


    /**
     * 完成分片上传
     *
     * @param uploadId
     * @param yourObjectName 文件位置
     * @return
     */
    String complete(String uploadId, String yourObjectName);

    /**
     * 取消分片数据
     *
     * @param uploadId
     * @param yourObjectName 文件位置
     */
    void cancel(String uploadId, String yourObjectName);
}
