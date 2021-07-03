//package com.github.ren.file.sdk.fdfs;
//
//import com.alibaba.fastjson.JSON;
//import com.alibaba.fastjson.JSONObject;
//import com.github.ren.file.sdk.FileClient;
//import com.github.ren.file.sdk.ex.ClientException;
//import com.github.ren.file.sdk.ex.FileIOException;
//import com.github.ren.file.sdk.lock.FileLock;
//import com.github.ren.file.sdk.lock.LocalLock;
//import com.github.ren.file.sdk.model.FastDFSUploadResult;
//import com.github.ren.file.sdk.part.CompleteMultipartResponse;
//import com.github.ren.file.sdk.part.InitMultipartResponse;
//import com.github.ren.file.sdk.part.PartInfo;
//import com.github.ren.file.sdk.part.UploadPart;
//import com.github.ren.file.sdk.util.Util;
//import lombok.Data;
//import lombok.EqualsAndHashCode;
//import lombok.ToString;
//import org.apache.commons.io.FileUtils;
//import org.apache.commons.io.FilenameUtils;
//import org.apache.commons.io.IOUtils;
//import org.apache.commons.lang3.StringUtils;
//import org.apache.commons.lang3.SystemUtils;
//import org.apache.commons.lang3.concurrent.BasicThreadFactory;
//import org.apache.commons.lang3.time.DateUtils;
//import org.csource.common.MyException;
//import org.csource.common.NameValuePair;
//import org.csource.fastdfs.FileInfo;
//import org.csource.fastdfs.ProtoCommon;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.net.URL;
//import java.nio.charset.StandardCharsets;
//import java.util.*;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.ScheduledThreadPoolExecutor;
//import java.util.concurrent.TimeUnit;
//
///**
// * @Description fastdfs文件客戶端
// * @Author ren
// * @Since 1.0
// */
//public class FastDFSClient implements FileClient {
//
//    private static final Logger logger = LoggerFactory.getLogger(FastDFSClient.class);
//
//    private FileLock lock = new LocalLock();
//
//    private FastDFSCleanTask fastDFSCleanTask;
//
//    private String group;
//
//    private static class SingletonHolder {
//        private static final FastDFSClient INSTANCE = new FastDFSClient();
//    }
//
//    public static FastDFSClient getInstance() {
//        return SingletonHolder.INSTANCE;
//    }
//
//    private FastDFSClient() {
//    }
//
//    public FileLock getLock() {
//        return lock;
//    }
//
//    public void setLock(FileLock lock) {
//        this.lock = lock;
//    }
//
//    public String getGroup() {
//        return group;
//    }
//
//    public void setGroup(String group) {
//        this.group = group;
//    }
//
//    private FastDFS client() {
//        return FastDFSBuilder.build();
//    }
//
//    /**
//     * 设置分片自动过期策略
//     *
//     * @param expirationDays 过期天数
//     */
//    public void setPartExpirationDays(int expirationDays) {
//        //1小时轮询一次 进行删除过期分片
//        fastDFSCleanTask = new FastDFSCleanTask(24 * expirationDays, 1);
//    }
//
//    @Override
//    public FastDFSUploadResult upload(File file, String objectName) {
//        try (InputStream is = new FileInputStream(file.getAbsolutePath())) {
//            FastDFS client = client();
//            byte[] content = IOUtils.toByteArray(is);
//            String[] result = client.upload_file(group, content, FilenameUtils.getExtension(file.getName()), null);
//            String group = result[0];
//            String path = result[1];
//            return new FastDFSUploadResult(group, path, Util.eTag(content));
//        } catch (IOException | MyException e) {
//            throw new ClientException(e.getMessage());
//        }
//    }
//
//    @Override
//    public FastDFSUploadResult upload(InputStream is, String objectName) {
//        try {
//            FastDFS client = client();
//            byte[] content = IOUtils.toByteArray(is);
//            String[] result = client.upload_file(group, content, FilenameUtils.getExtension(objectName), null);
//            String group = result[0];
//            String path = result[1];
//            return new FastDFSUploadResult(group, path, Util.eTag(content));
//        } catch (IOException | MyException e) {
//            throw new ClientException(e.getMessage());
//        }
//    }
//
//    @Override
//    public FastDFSUploadResult upload(byte[] content, String objectName) {
//        try {
//            FastDFS client = client();
//            String[] result = client.upload_file(group, content, FilenameUtils.getExtension(objectName), null);
//            String group = result[0];
//            String path = result[1];
//            return new FastDFSUploadResult(group, path, Util.eTag(content));
//        } catch (IOException | MyException e) {
//            throw new ClientException(e.getMessage());
//        }
//    }
//
//    @Override
//    public FastDFSUploadResult upload(String url, String objectName) {
//        try (InputStream is = new URL(url).openStream()) {
//            return this.upload(is, objectName);
//        } catch (IOException e) {
//            throw new FileIOException("fdfs upload url file error", e);
//        }
//    }
//
//    protected String getPath(String objectName) {
//        return objectName.substring(objectName.indexOf(Util.SLASH) + 1);
//    }
//
//    protected String getGroup(String objectName) {
//        return objectName.substring(0, objectName.indexOf(Util.SLASH));
//    }
//
//    @Override
//    public InitMultipartResponse initiateMultipartUpload(String objectName) {
//        try {
//            FastDFS client = client();
//            //初始化 append文件信息
//            String[] result = client.upload_appender_file(group, "".getBytes(StandardCharsets.UTF_8),
//                    FilenameUtils.getExtension(objectName), null);
//            String group = result[0];
//            String path = result[1];
//            objectName = group + Util.SLASH + path;
//            String uploadId = initUploadId(objectName);
//            if (fastDFSCleanTask != null) {
//                fastDFSCleanTask.addUpload(uploadId, objectName);
//            }
//            return new InitMultipartResponse(uploadId, objectName);
//        } catch (IOException | MyException e) {
//            throw new ClientException(e);
//        }
//    }
//
//    private String initUploadId(String objectName) throws MyException, IOException {
//        String uploadId = Util.eTag(objectName);
//        String group = getGroup(objectName);
//        String path = getPath(objectName);
//        mergeMetadata(group, path, FastDFSConstants.UPLOAD_ID, uploadId);
//        return uploadId;
//    }
//
//    private void deleteUploadId(String objectName) throws MyException, IOException {
//        String group = getGroup(objectName);
//        String path = getPath(objectName);
//        mergeMetadata(group, path, FastDFSConstants.UPLOAD_ID, "");
//    }
//
//    private void checkUploadId(String objectName) throws MyException, IOException {
//        String group = getGroup(objectName);
//        String path = getPath(objectName);
//        String metadata = getMetadata(group, path, FastDFSConstants.UPLOAD_ID);
//        if (metadata == null) {
//            throw new MyException("uploadId not found maybe complete or abort upload");
//        }
//    }
//
//    private void mergeMetadata(String group, String path, String key, String value) throws MyException, IOException {
//        FastDFS client = client();
//        NameValuePair[] metaList = new NameValuePair[]{
//                new NameValuePair(key, value)
//        };
//        client.set_metadata(group, path, metaList, ProtoCommon.STORAGE_SET_METADATA_FLAG_MERGE);
//    }
//
//    private String getMetadata(String group, String path, String key) throws MyException, IOException {
//        FastDFS client = client();
//        NameValuePair[] metadata = client.get_metadata(group, path);
//        if (metadata != null) {
//            for (NameValuePair pair : metadata) {
//                String name = pair.getName();
//                String value = pair.getValue();
//                if (name.equals(key) && StringUtils.isNotBlank(value)) {
//                    return value;
//                }
//            }
//        }
//        return null;
//    }
//
//    @Override
//    public PartInfo uploadPart(UploadPart part) {
//        String uploadId = part.getUploadId();
//        String objectName = part.getObjectName();
//        long partSize = part.getPartSize();
//        String group = getGroup(objectName);
//        String path = getPath(objectName);
//        int partNumber = part.getPartNumber();
//        FastDFS client = client();
//        FastDfsPartInfo partInfo = new FastDfsPartInfo();
//        partInfo.setPartNumber(partNumber);
//        partInfo.setPartSize(partSize);
//        partInfo.setUploadId(uploadId);
//        partInfo.setGroup(group);
//        String lockKey = uploadId + partNumber;
//        //源文件为 xxx.mp4 分片文件为 xxx-1.mp4
//        String partPath = getPartPath(path, partNumber);
//        try {
//            try {
//                lock.lock(lockKey);
//                //判断uploadId是否存在
//                checkUploadId(objectName);
//                FileInfo fileInfo = client.query_file_info(group, partPath);
//                byte[] body = IOUtils.toByteArray(part.getInputStream());
//                if (fileInfo == null) {
//                    String[] partFile = client.upload_file(group, path, Util.DASHED + partNumber, body,
//                            FilenameUtils.getExtension(path), null);
//                    partInfo.setPath(partFile[1]);
//                    partInfo.setETag(Util.eTag(body));
//                } else {
//                    client.delete_file(group, partPath);
//                    String[] partFile = client.upload_file(group, path, Util.DASHED + partNumber, body,
//                            FilenameUtils.getExtension(path), null);
//                    partInfo.setPath(partFile[1]);
//                    partInfo.setETag(Util.eTag(body));
//                }
//                //处理part文件 metadata
//                mergeMetadata(group, partPath, FastDFSConstants.PART, JSON.toJSONString(partInfo));
//            } finally {
//                lock.unlock(lockKey);
//            }
//
//            lock.lock(uploadId);
//            //处理append文件part metadata
//            String metadata = getMetadata(group, path, FastDFSConstants.PART + partNumber);
//            if (metadata == null) {
//                mergeMetadata(group, path, FastDFSConstants.PART + partNumber, JSON.toJSONString(partInfo));
//            }
//            lock.unlock(uploadId);
//
//            appendPartBackground(uploadId, part.getObjectName());
//            return partInfo;
//        } catch (IOException | MyException e) {
//            try {
//                client.delete_file(group, partPath);
//                mergeMetadata(group, path, FastDFSConstants.PART + partNumber, "");
//            } catch (IOException | MyException ioException) {
//                logger.error("delete upload part error", e);
//            }
//            throw new ClientException("upload part error", e);
//        }
//    }
//
//    private String getPartPath(String path, Integer partNumber) {
//        String baseName = FilenameUtils.getBaseName(path);
//        return path.replace(baseName, baseName + Util.DASHED + partNumber);
//    }
//
//    private void appendPartBackground(String uploadId, String objectName) throws MyException, IOException {
//        try {
//            lock.lock(uploadId);
//            FastDFS client = client();
//            String group = getGroup(objectName);
//            String path = getPath(objectName);
//            checkUploadId(objectName);
//            NameValuePair[] metadata = client.get_metadata(group, path);
//            List<FastDfsPartInfo> partInfos = new ArrayList<>();
//            int nextPartNumber = 1;
//            if (metadata != null) {
//                for (NameValuePair pair : metadata) {
//                    String name = pair.getName();
//                    String value = pair.getValue();
//                    if (FastDFSConstants.UPLOAD_ID.equals(name)) {
//                        continue;
//                    }
//                    if (FastDFSConstants.NEXT_PART_NUMBER_KEY.equals(name)) {
//                        nextPartNumber = Integer.parseInt(value);
//                        continue;
//                    }
//                    logger.info("name={} value={}",name,value);
//                    partInfos.add(JSON.parseObject(value, FastDfsPartInfo.class));
//                }
//            }
////            for (FastDfsPartInfo partInfo : partInfos) {
////                logger.info("partInfo={}",partInfo);
////            }
//            //part 排序
//            partInfos.sort(Comparator.comparingInt(PartInfo::getPartNumber));
//            long fileOffset = 0;
//            for (FastDfsPartInfo fastDfsPartInfo : partInfos) {
//                int partNumber = fastDfsPartInfo.getPartNumber();
//                if (partNumber < nextPartNumber) {
//                    // Part already appended.
//                    fileOffset += fastDfsPartInfo.getPartSize();
//                    continue;
//                }
//                if (partNumber > nextPartNumber) {
//                    // Required part number is not yet uploaded.
//                    break;
//                }
//                client.modify_file(group, path, fileOffset, client.download_file(fastDfsPartInfo.getGroup(), fastDfsPartInfo.getPath()));
//                fileOffset += fastDfsPartInfo.getPartSize();
//                nextPartNumber++;
//            }
//            mergeMetadata(group, path, FastDFSConstants.NEXT_PART_NUMBER_KEY, String.valueOf(nextPartNumber));
//        } finally {
//            lock.unlock(uploadId);
//        }
//    }
//
//    @ToString(callSuper = true)
//    @EqualsAndHashCode(callSuper = true)
//    @Data
//    static class FastDfsPartInfo extends PartInfo {
//
//        private String group;
//
//        private String path;
//    }
//
//    @Override
//    public List<PartInfo> listParts(String uploadId, String objectName) {
//        try {
//            checkUploadId(objectName);
//            FastDFS client = client();
//            String group = getGroup(objectName);
//            String path = getPath(objectName);
//            List<PartInfo> partInfos = new ArrayList<>();
//            NameValuePair[] metadata = client.get_metadata(group, path);
//            if (metadata != null) {
//                for (NameValuePair pair : metadata) {
//                    String name = pair.getName();
//                    String value = pair.getValue();
//                    if (FastDFSConstants.constants.contains(name)) {
//                        continue;
//                    }
//                    partInfos.add(JSON.parseObject(value, FastDfsPartInfo.class));
//                }
//            }
//            partInfos.sort(Comparator.comparingInt(PartInfo::getPartNumber));
//            return partInfos;
//        } catch (IOException | MyException e) {
//            throw new ClientException(e);
//        }
//    }
//
//    @Override
//    public CompleteMultipartResponse completeMultipartUpload(String uploadId, String objectName, List<PartInfo> parts) {
//        try {
//            //处理可能没有上传的分片
//            appendPartBackground(uploadId, objectName);
//            lock.lock(uploadId);
//            FastDFS client = client();
//            String group = getGroup(objectName);
//            String path = getPath(objectName);
//            boolean appendFallback = true;
//            List<PartInfo> partInfos = listParts(uploadId, objectName);
//            //分片数量一致校验
//            if (partInfos.size() == parts.size()) {
//                for (int i = 0; i < parts.size(); i++) {
//                    //判断文件eTag值是否一致
//                    if (!parts.get(i).getETag().equals(partInfos.get(i).getETag())) {
//                        break;
//                    }
//                    //判断分片number是否一致
//                    if (parts.get(i).getPartNumber() != partInfos.get(i).getPartNumber()) {
//                        break;
//                    }
//
//                    if (i == parts.size() - 1) {
//                        appendFallback = false;
//                        break;
//                    }
//                }
//            }
//            if (appendFallback) {
//                long fileOffset = 0;
//                for (PartInfo partInfo : parts) {
//                    int partNumber = partInfo.getPartNumber();
//                    String partPath = getPartPath(path, partNumber);
//                    String partJson = getMetadata(group, partPath, FastDFSConstants.PART);
//                    FastDfsPartInfo fastDfsPartInfo = JSON.parseObject(partJson, FastDfsPartInfo.class);
//                    if (fastDfsPartInfo == null) {
//                        throw new MyException("the part seem to have got lost");
//                    }
//                    client.modify_file(group, path, fileOffset, client.download_file(fastDfsPartInfo.getGroup(), fastDfsPartInfo.getPath()));
//                    fileOffset += partInfo.getPartSize();
//                }
//                FileInfo fileInfo = client.query_file_info(group, path);
//                //修正文件实际大小
//                if (fileInfo.getFileSize() != fileOffset) {
//                    client.truncate_file(group, path, fileOffset);
//                }
//            }
//            //删除分片信息
//            deleteUploadId(objectName);
//            for (PartInfo partInfo : partInfos) {
//                int partNumber = partInfo.getPartNumber();
//                String partPath = getPartPath(path, partNumber);
//                client.delete_file(group, partPath);
//            }
//            client.set_metadata(group, path, null, ProtoCommon.STORAGE_SET_METADATA_FLAG_OVERWRITE);
//            if (fastDFSCleanTask != null) {
//                fastDFSCleanTask.removeUpload(uploadId);
//            }
//            String eTag = Util.completeMultipartMd5(parts);
//            return new CompleteMultipartResponse(eTag, group + Util.SLASH + path);
//        } catch (IOException | MyException e) {
//            abortMultipartUpload(uploadId, objectName);
//            throw new ClientException(e);
//        } finally {
//            lock.unlock(uploadId);
//        }
//    }
//
//    @Override
//    public void abortMultipartUpload(String uploadId, String objectName) {
//        try {
//            try {
//                lock.lock(uploadId);
//                deleteUploadId(objectName);
//                FastDFS client = client();
//                String group = getGroup(objectName);
//                String path = getPath(objectName);
//                //先删除part文件信息
//                List<PartInfo> partInfos = listParts(uploadId, objectName);
//                for (PartInfo partInfo : partInfos) {
//                    String lockKey = uploadId + partInfo.getPartNumber();
//                    try {
//                        lock.lock(lockKey);
//                        String partPath = getPartPath(path, partInfo.getPartNumber());
//                        client.delete_file(group, partPath);
//                    } finally {
//                        lock.unlock(lockKey);
//                    }
//                }
//                client.delete_file(group, path);
//                if (fastDFSCleanTask != null) {
//                    fastDFSCleanTask.removeUpload(uploadId);
//                }
//            } finally {
//                lock.unlock(uploadId);
//            }
//        } catch (IOException | MyException e) {
//            throw new ClientException(e);
//        }
//    }
//
//    @Override
//    public int getPartExpirationDays() {
//        if (fastDFSCleanTask != null) {
//            return fastDFSCleanTask.expire / 24;
//        }
//        return -1;
//    }
//
//    /**
//     * @Description fastdfs清理任务
//     * @Author ren
//     * @Since 1.0
//     */
//    class FastDFSCleanTask {
//
//        private String uploadGroup;
//        private String uploadPath;
//
//        /**
//         * 过期时间 24h
//         */
//        private int expire = 24;
//        /**
//         * 清理时间间隔 12h
//         */
//        private long period = 12;
//
//        private final ScheduledExecutorService executorService =
//                new ScheduledThreadPoolExecutor(1, new BasicThreadFactory.Builder().namingPattern("clean-pool-%d")
//                        .daemon(true).build());
//
//        public FastDFSCleanTask() {
//            init();
//            executorService.scheduleAtFixedRate(this::clean, 0, period, TimeUnit.HOURS);
//        }
//
//        public FastDFSCleanTask(int expire, long period) {
//            this.expire = expire;
//            this.period = period;
//            init();
//            executorService.scheduleAtFixedRate(this::clean, 0, period, TimeUnit.HOURS);
//        }
//
//        public void addUpload(String uploadId, String objectName) {
//            try {
//                FastDFS client = client();
//                String group = getGroup(objectName);
//                String path = getPath(objectName);
//                FileInfo fileInfo = client.query_file_info(group, path);
//                Map<String, Object> map = new HashMap<>(3);
//                map.put("uploadId", uploadId);
//                map.put("objectName", objectName);
//                map.put("createTime", fileInfo.getCreateTimestamp());
//                NameValuePair[] metaList = new NameValuePair[]{
//                        new NameValuePair(uploadId, JSON.toJSONString(map))
//                };
//                client.set_metadata(uploadGroup, uploadPath, metaList, ProtoCommon.STORAGE_SET_METADATA_FLAG_MERGE);
//            } catch (Exception e) {
//                throw new ClientException(e);
//            }
//        }
//
//        public void removeUpload(String uploadId) {
//            try {
//                FastDFS client = client();
//                NameValuePair[] metaList = new NameValuePair[]{
//                        new NameValuePair(uploadId, "")
//                };
//                client.set_metadata(uploadGroup, uploadPath, metaList, ProtoCommon.STORAGE_SET_METADATA_FLAG_MERGE);
//            } catch (Exception e) {
//                throw new ClientException(e);
//            }
//        }
//
//        private void init() {
//            try {
//                File userDir = SystemUtils.getUserDir();
//                String uploadFile = "upload.json";
//                File file = new File(userDir, uploadFile);
//                if (file.exists()) {
//                    FastDFS client = client();
//                    String json = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
//                    JSONObject jsonObject = JSON.parseObject(json);
//                    uploadGroup = jsonObject.getString("uploadGroup");
//                    uploadPath = jsonObject.getString("uploadPath");
//                    FileInfo fileInfo = client.query_file_info(uploadGroup, uploadPath);
//                    if (fileInfo != null) {
//                        return;
//                    }
//                }
//                FastDFS client = client();
//                String[] result = client.upload_file(group, "".getBytes(StandardCharsets.UTF_8), "json", null);
//                uploadGroup = result[0];
//                uploadPath = result[1];
//                Map<String, Object> map = new HashMap<>(2);
//                map.put("uploadGroup", uploadGroup);
//                map.put("uploadPath", uploadPath);
//                FileUtils.writeStringToFile(file, JSON.toJSONString(map));
//            } catch (Exception e) {
//                throw new ClientException(e);
//            }
//        }
//
//        public void clean() {
//            try {
//                FastDFS client = client();
//                NameValuePair[] uploadMetadata = client.get_metadata(uploadGroup, uploadPath);
//                List<NameValuePair> uploadMetadataNew = new ArrayList<>();
//                if (uploadMetadata != null) {
//                    for (NameValuePair uploadPair : uploadMetadata) {
//                        String uploadId = uploadPair.getName();
//                        String value = uploadPair.getValue();
//                        //uploadId is remove
//                        if ("".equals(value)) {
//                            continue;
//                        }
//                        JSONObject jsonObject = JSON.parseObject(value);
//                        Date createTime = jsonObject.getDate("createTime");
//                        String objectName = jsonObject.getString("objectName");
//                        Date expireTime = DateUtils.addHours(createTime, expire);
//                        if (new Date().after(expireTime)) {
//                            deleteUploadId(objectName);
//                            String group = getGroup(objectName);
//                            String path = getPath(objectName);
//                            List<PartInfo> partInfos = new ArrayList<>();
//                            NameValuePair[] metadata = client.get_metadata(group, path);
//                            if (metadata != null) {
//                                for (NameValuePair pair : metadata) {
//                                    String name = pair.getName();
//                                    if (FastDFSConstants.constants.contains(name)) {
//                                        continue;
//                                    }
//                                    partInfos.add(JSON.parseObject(pair.getValue(), FastDfsPartInfo.class));
//                                }
//                                for (PartInfo partInfo : partInfos) {
//                                    String partPath = getPartPath(path, partInfo.getPartNumber());
//                                    client.delete_file(group, partPath);
//                                }
//                                client.delete_file(group, path);
//                            }
//                        } else {
//                            uploadMetadataNew.add(uploadPair);
//                        }
//                    }
//                    NameValuePair[] nameValuePairs = uploadMetadataNew.toArray(new NameValuePair[0]);
//                    client.set_metadata(uploadGroup, uploadPath, nameValuePairs, ProtoCommon.STORAGE_SET_METADATA_FLAG_OVERWRITE);
//                }
//            } catch (Exception e) {
//                logger.error("清理临时分片任务失败", e);
//            }
//        }
//    }
//
//}