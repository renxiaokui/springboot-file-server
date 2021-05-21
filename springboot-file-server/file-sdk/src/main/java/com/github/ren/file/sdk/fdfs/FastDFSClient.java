package com.github.ren.file.sdk.fdfs;

import com.alibaba.fastjson.JSON;
import com.github.ren.file.sdk.FileClient;
import com.github.ren.file.sdk.util.Util;
import com.github.ren.file.sdk.ex.ClientException;
import com.github.ren.file.sdk.ex.FileIOException;
import com.github.ren.file.sdk.lock.FileLock;
import com.github.ren.file.sdk.lock.LocalLock;
import com.github.ren.file.sdk.model.FdfsUploadResult;
import com.github.ren.file.sdk.part.CompleteMultipart;
import com.github.ren.file.sdk.part.InitMultipartResult;
import com.github.ren.file.sdk.part.PartInfo;
import com.github.ren.file.sdk.part.UploadPart;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.csource.common.MyException;
import org.csource.common.NameValuePair;
import org.csource.fastdfs.FileInfo;
import org.csource.fastdfs.ProtoCommon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @Description fastdfs文件客戶端
 * @Author ren
 * @Since 1.0
 */
public class FastDFSClient implements FileClient {

    private static final Logger logger = LoggerFactory.getLogger(FastDFSClient.class);

    private FileLock lock = new LocalLock();

    private String bucketName;

    private static class SingletonHolder {
        private static FastDFSClient instance = new FastDFSClient();
    }

    public static FastDFSClient getInstance() {
        return SingletonHolder.instance;
    }

    private FastDFSClient() {
    }

    public FileLock getLock() {
        return lock;
    }

    public void setLock(FileLock lock) {
        this.lock = lock;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    private FastDFS client() {
        return FastDFSBuilder.build();
    }

    @Override
    public FdfsUploadResult upload(File file, String yourObjectName) {
        try (InputStream is = new FileInputStream(file.getAbsolutePath())) {
            FastDFS client = client();
            String[] result = client.upload_file(bucketName, IOUtils.toByteArray(is), FilenameUtils.getExtension(file.getName()), null);
            String group = result[0];
            String path = result[1];
            return new FdfsUploadResult(group, path, Util.eTag(is));
        } catch (IOException | MyException e) {
            throw new ClientException(e.getMessage());
        }
    }

    @Override
    public FdfsUploadResult upload(InputStream is, String yourObjectName) {
        try {
            FastDFS client = client();
            String[] result = client.upload_file(bucketName, IOUtils.toByteArray(is), FilenameUtils.getExtension(yourObjectName), null);
            String group = result[0];
            String path = result[1];
            return new FdfsUploadResult(group, path, Util.eTag(is));
        } catch (IOException | MyException e) {
            throw new ClientException(e.getMessage());
        }
    }

    @Override
    public FdfsUploadResult upload(byte[] content, String yourObjectName) {
        try {
            FastDFS client = client();
            String[] result = client.upload_file(bucketName, content, FilenameUtils.getExtension(yourObjectName), null);
            String group = result[0];
            String path = result[1];
            return new FdfsUploadResult(group, path);
        } catch (IOException | MyException e) {
            throw new ClientException(e.getMessage());
        }
    }

    @Override
    public FdfsUploadResult upload(String url, String yourObjectName) {
        try (InputStream is = new URL(url).openStream()) {
            return this.upload(is, yourObjectName);
        } catch (IOException e) {
            throw new FileIOException("fdfs upload url file error", e);
        }
    }

    protected String getPath(String objectName) {
        return objectName.substring(objectName.indexOf(Util.SLASH) + 1);
    }

    protected String getGroup(String objectName) {
        return objectName.substring(0, objectName.indexOf(Util.SLASH));
    }

    @Override
    public InitMultipartResult initiateMultipartUpload(String yourObjectName) {
        try {
            FastDFS client = client();
            //初始化 append文件信息
            String[] result = client.upload_appender_file(bucketName, "".getBytes(StandardCharsets.UTF_8),
                    FilenameUtils.getExtension(yourObjectName), null);
            String group = result[0];
            String path = result[1];
            String objectName = group + Util.SLASH + path;
            String uploadId = initUploadId(objectName);
            return new InitMultipartResult(uploadId, objectName);
        } catch (IOException | MyException e) {
            throw new ClientException(e);
        }
    }

    private String initUploadId(String objectName) throws MyException, IOException {
        String uploadId = Util.eTag(objectName);
        String group = getGroup(objectName);
        String path = getPath(objectName);
        setPartMetadata(group, path, FastDfsConstants.UPLOAD_ID, uploadId);
        return uploadId;
    }

    private NameValuePair[] checkUploadId(String uploadId, String objectName) throws MyException, IOException {
        FastDFS client = client();
        String group = getGroup(objectName);
        String path = getPath(objectName);
        NameValuePair[] metadata = client.get_metadata(group, path);
        if (metadata == null) {
            throw new MyException("uploadId not found maybe complete or abort upload");
        }
        boolean check = true;
        for (NameValuePair pair : metadata) {
            String name = pair.getName();
            String value = pair.getValue();
            if (FastDfsConstants.UPLOAD_ID.equals(name) && uploadId.equals(value)) {
                check = false;
                break;
            }
        }
        if (check) {
            throw new MyException("uploadId not found maybe complete or abort upload");
        }
        return metadata;
    }

    private void deleteUploadId(String objectName) throws MyException, IOException {
        FastDFS client = client();
        String group = getGroup(objectName);
        String path = getPath(objectName);
        client.set_metadata(group, path, null, ProtoCommon.STORAGE_SET_METADATA_FLAG_OVERWRITE);
    }

    @Override
    public PartInfo uploadPart(UploadPart part) {
        String uploadId = part.getUploadId();
        String objectName = part.getObjectName();
        long partSize = part.getPartSize();
        String group = getGroup(objectName);
        String path = getPath(objectName);
        int partNumber = part.getPartNumber();
        FastDFS client = client();
        FastDfsPartInfo partInfo = new FastDfsPartInfo();
        partInfo.setPartNumber(partNumber);
        partInfo.setPartSize(partSize);
        partInfo.setUploadId(uploadId);
        partInfo.setGroup(group);
        String lockKey = uploadId + partNumber;
        try {
            lock.lock(lockKey);
            //判断uploadId是否存在
            checkUploadId(uploadId, objectName);
            //源文件为 xxx.mp4 分片文件为 xxx-1.mp4
            String partPath = getPartPath(path, partNumber);
            FileInfo fileInfo = client.query_file_info(group, partPath);
            byte[] body = IOUtils.toByteArray(part.getInputStream());
            if (fileInfo == null) {
                String[] partFile = client.upload_file(group, path, Util.DASHED + partNumber, body,
                        FilenameUtils.getExtension(path), null);
                partInfo.setPath(partFile[1]);
                partInfo.setETag(Util.eTag(body));
                //处理append文件 metadata
                setPartMetadata(group, path, String.valueOf(partNumber), JSON.toJSONString(partInfo));
            } else {
                client.delete_file(group, partPath);
                String[] partFile = client.upload_file(group, path, Util.DASHED + partNumber, body,
                        FilenameUtils.getExtension(path), null);
                partInfo.setPath(partFile[1]);
                partInfo.setETag(Util.eTag(body));
            }
            //处理part文件 metadata
            setPartMetadata(group, partPath, String.valueOf(partNumber), JSON.toJSONString(partInfo));
        } catch (IOException | MyException e) {
            throw new ClientException(e);
        } finally {
            lock.unlock(lockKey);
        }
        try {
            appendPartBackground(uploadId, part.getObjectName());
        } catch (MyException | IOException e) {
            throw new ClientException(e);
        }
        return partInfo;

    }

    private String getPartPath(String path, Integer partNumber) {
        String baseName = FilenameUtils.getBaseName(path);
        return path.replace(baseName, baseName + Util.DASHED + partNumber);
    }

    private void setPartMetadata(String group, String path, String key, String value) throws MyException, IOException {
        FastDFS client = client();
        NameValuePair[] metaList = new NameValuePair[]{
                new NameValuePair(key, value)
        };
        client.set_metadata(group, path, metaList, ProtoCommon.STORAGE_SET_METADATA_FLAG_MERGE);
    }

    protected void appendPartBackground(String uploadId, String objectName) throws MyException, IOException {
        try {
            lock.lock(uploadId);
            FastDFS client = client();
            String group = getGroup(objectName);
            String path = getPath(objectName);
            NameValuePair[] metadata = checkUploadId(uploadId, objectName);
            List<FastDfsPartInfo> partInfos = new ArrayList<>();
            int nextPartNumber = 1;
            for (NameValuePair pair : metadata) {
                String name = pair.getName();
                String value = pair.getValue();
                if (FastDfsConstants.UPLOAD_ID.equals(name)) {
                    continue;
                }
                if (FastDfsConstants.NEXT_PART_NUMBER_KEY.equals(name)) {
                    nextPartNumber = Integer.parseInt(value);
                    continue;
                }
                partInfos.add(JSON.parseObject(value, FastDfsPartInfo.class));
            }

            //获取object文件
            partInfos.sort(Comparator.comparingInt(PartInfo::getPartNumber));
            for (FastDfsPartInfo fastDfsPartInfo : partInfos) {
                int partNumber = fastDfsPartInfo.getPartNumber();
                if (partNumber < nextPartNumber) {
                    // Part already appended.
                    continue;
                }
                if (partNumber > nextPartNumber) {
                    // Required part number is not yet uploaded.
                    break;
                }
                client.append_file(group, path, client.download_file(fastDfsPartInfo.getGroup(), fastDfsPartInfo.getPath()));
                //处理append文件 metadata
                setPartMetadata(group, path, String.valueOf(partNumber), JSON.toJSONString(fastDfsPartInfo));
                nextPartNumber++;
            }
            setPartMetadata(group, path, FastDfsConstants.NEXT_PART_NUMBER_KEY, String.valueOf(nextPartNumber));
        } finally {
            lock.unlock(uploadId);
        }
    }

    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    @Data
    static class FastDfsPartInfo extends PartInfo {

        private String group;

        private String path;
    }

    @Override
    public List<PartInfo> listParts(String uploadId, String yourObjectName) {
        List<PartInfo> partInfos = new ArrayList<>();
        try {
            NameValuePair[] metadata = checkUploadId(uploadId, yourObjectName);
            for (NameValuePair pair : metadata) {
                String name = pair.getName();
                String value = pair.getValue();
                if (FastDfsConstants.UPLOAD_ID.equals(name)) {
                    continue;
                }
                if (FastDfsConstants.NEXT_PART_NUMBER_KEY.equals(name)) {
                    continue;
                }
                partInfos.add(JSON.parseObject(value, PartInfo.class));
            }
        } catch (IOException | MyException e) {
            throw new ClientException(e);
        }
        return partInfos;
    }

    @Override
    public CompleteMultipart completeMultipartUpload(String uploadId, String yourObjectName) {
        try {
            List<PartInfo> partInfos = listParts(uploadId, yourObjectName);
            String eTag = Util.completeMultipartMd5(partInfos);
            FastDFS client = client();
            String group = getGroup(yourObjectName);
            String path = getPath(yourObjectName);

            boolean appendFallback = false;
            //处理可能没有上传的分片
            appendPartBackground(uploadId, yourObjectName);
            try {
                lock.lock(uploadId);
                NameValuePair[] metadata = client.get_metadata(group, path);
                if (metadata == null) {
                    throw new ClientException("uploadId not found maybe complete or abort upload");
                }
                partInfos.sort(Comparator.comparingInt(PartInfo::getPartNumber));
                long fileOffset = 0;
                for (PartInfo partInfo : partInfos) {
                    String partPath = getPartPath(path, partInfo.getPartNumber());
                    //获取分片信息
                    NameValuePair[] partMetadata = client.get_metadata(group, partPath);
                    FastDfsPartInfo fastDfsPartInfo = JSON.parseObject(partMetadata[0].getValue(), FastDfsPartInfo.class);
                    //判断文件eTag值是否一致
                    if (!partInfo.getETag().equals(fastDfsPartInfo.getETag())) {
                        if (partInfo.getPartSize() != fastDfsPartInfo.getPartSize()) {
                            appendFallback = true;
                            break;
                        }
                        //修复文件
                        client.modify_file(group, path, fileOffset, client.download_file(fastDfsPartInfo.getGroup(), fastDfsPartInfo.getPath()));
                    }
                    fileOffset += partInfo.getPartSize();
                }
                if (appendFallback) {
                    FileInfo fileInfo = client.query_file_info(group, path);
                    if (fileInfo.getFileSize() != 0) {
                        client.truncate_file(group, path);
                    }
                    for (PartInfo partInfo : partInfos) {
                        int partNumber = partInfo.getPartNumber();
                        String partPath = getPartPath(path, partNumber);
                        NameValuePair[] partMetadata = client.get_metadata(group, partPath);
                        FastDfsPartInfo fastDfsPartInfo = JSON.parseObject(partMetadata[0].getValue(), FastDfsPartInfo.class);
                        client.append_file(group, path, client.download_file(fastDfsPartInfo.getGroup(), fastDfsPartInfo.getPath()));
                    }
                }
                //删除分片信息
                deleteUploadId(yourObjectName);
                for (PartInfo partInfo : partInfos) {
                    int partNumber = partInfo.getPartNumber();
                    String partPath = getPartPath(path, partNumber);
                    client.delete_file(group, partPath);
                }
            } finally {
                lock.unlock(uploadId);
            }
            return new CompleteMultipart(eTag, group + Util.SLASH + path);
        } catch (IOException | MyException e) {
            throw new ClientException(e);
        }
    }

    @Override
    public void abortMultipartUpload(String uploadId, String yourObjectName) {
        try {
            FastDFS client = client();
            try {
                lock.lock(uploadId);
                String group = getGroup(yourObjectName);
                String path = getPath(yourObjectName);
                //先删除part文件信息
                List<PartInfo> partInfos = listParts(uploadId, yourObjectName);
                for (PartInfo partInfo : partInfos) {
                    String lockKey = uploadId + partInfo.getPartNumber();
                    try {
                        lock.lock(lockKey);
                        String partPath = getPartPath(path, partInfo.getPartNumber());
                        client.delete_file(group, partPath);
                    } finally {
                        lock.unlock(lockKey);
                    }
                }
                deleteUploadId(yourObjectName);
                //删除源文件
                client.delete_file(group, path);
            } finally {
                lock.unlock(uploadId);
            }
        } catch (IOException | MyException e) {
            throw new ClientException(e);
        }
    }

}