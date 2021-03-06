//package com.github.ren.file.client.local;
//
//import com.github.ren.file.client.FileClient;
//import com.github.ren.file.client.ex.FileIOException;
//import com.github.ren.file.client.model.UploadGenericResult;
//import com.github.ren.file.client.part.*;
//import com.github.ren.file.client.util.Util;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.*;
//import java.net.URL;
//import java.nio.channels.FileChannel;
//import java.nio.file.Paths;
//import java.util.List;
//import java.util.UUID;
//
///**
// * @Description 本地文件客户端
// * @Author ren
// * @Since 1.0
// */
//public class LocalClient implements FileClient {
//
//    private static final Logger logger = LoggerFactory.getLogger(LocalClient.class);
//
//    /**
//     * 文件存储目录
//     */
//    private String localStore;
//
//    protected static final String PART_DIR = "/data/part";
//
//    private PartStore partStore = new LocalPartStore(PART_DIR);
//
//    public LocalClient(String localStore) {
//        this.localStore = localStore;
//    }
//
//    public LocalClient(String localStore, PartStore partStore) {
//        this.localStore = localStore;
//        this.partStore = partStore;
//    }
//
//    public void setLocalStore(String localStore) {
//        this.localStore = localStore;
//    }
//
//    public PartStore getPartStore() {
//        return partStore;
//    }
//
//    public void setPartStore(PartStore partStore) {
//        this.partStore = partStore;
//    }
//
//    public String getLocalStore() {
//        File fileDir = new File(localStore);
//        if (!fileDir.exists() && !fileDir.mkdirs()) {
//            throw new RuntimeException("localStore mkdirs error");
//        }
//        return localStore;
//    }
//
//    public File getOutFile(String objectName) {
//        String relativePath = Paths.get(getLocalStore(), objectName).toString();
//        File fileDir = new File(relativePath).getParentFile();
//        if (!fileDir.exists() && !fileDir.mkdirs()) {
//            throw new RuntimeException("local mkdirs error");
//        }
//        return new File(relativePath);
//    }
//
//    @Override
//    public UploadGenericResult upload(File file, String objectName) {
//        try {
//            File outFile = getOutFile(objectName);
//            LocalFileOperation.copyFile(file, outFile);
//            String eTag = Util.eTag(outFile);
//            return new UploadGenericResult(objectName, eTag);
//        } catch (IOException e) {
//            throw new FileIOException("local upload file error", e);
//        }
//    }
//
//    @Override
//    public UploadGenericResult upload(InputStream is, String objectName) {
//        try {
//            File outFile = getOutFile(objectName);
//            LocalFileOperation.copyFile(is, outFile);
//            String eTag = Util.eTag(outFile);
//            return new UploadGenericResult(objectName, eTag);
//        } catch (IOException e) {
//            throw new FileIOException("local upload InputStream error", e);
//        } finally {
//            Util.close(is);
//        }
//    }
//
//    @Override
//    public UploadGenericResult upload(byte[] content, String objectName) {
//        File outFile = this.getOutFile(objectName);
//        try (ByteArrayInputStream is = new ByteArrayInputStream(content)) {
//            LocalFileOperation.copyFile(is, outFile);
//            String eTag = Util.eTag(outFile);
//            return new UploadGenericResult(objectName, eTag);
//        } catch (IOException e) {
//            throw new FileIOException("local byte[] upload error", e);
//        }
//    }
//
//    @Override
//    public UploadGenericResult upload(String url, String objectName) {
//        try (InputStream is = new URL(url).openStream()) {
//            return this.upload(is, objectName);
//        } catch (IOException e) {
//            throw new FileIOException("local upload url file error", e);
//        }
//    }
//
//    @Override
//    public InitMultipartResponse initMultipartUpload(InitMultipartUploadArgs args) {
//        String uploadId = UUID.randomUUID().toString().replace("-", "");
//        return new InitMultipartResponse(uploadId, args.getObjectName());
//    }
//
//    @Override
//    public UploadMultipartResponse uploadMultipart(UploadPartArgs part) {
//        UploadMultipartResponse uploadMultipartResponse = new UploadMultipartResponse();
//        uploadMultipartResponse.setPartSize(part.getPartSize());
//        uploadMultipartResponse.setUploadId(part.getUploadId());
//        uploadMultipartResponse.setPartNumber(part.getPartNumber());
//        uploadMultipartResponse.setETag(partStore.uploadPart(part));
//        return uploadMultipartResponse;
//    }
//
//    @Override
//    public List<UploadMultipartResponse> listMultipartUpload(ListMultipartUploadArgs args) {
//        String uploadId = args.getUploadId();
//        String objectName = args.getObjectName();
//        return partStore.listParts(uploadId, objectName);
//    }
//
//    @Override
//    public CompleteMultipartResponse completeMultipartUpload(String uploadId, String objectName, List<UploadMultipartResponse> parts) {
//        File outFile = this.getOutFile(objectName);
//        try (FileChannel outChannel = new FileOutputStream(outFile).getChannel()) {
//            //同步nio 方式对分片进行合并, 有效的避免文件过大导致内存溢出
//            for (UploadMultipartResponse uploadMultipartResponse : parts) {
//                UploadPartArgs uploadPartArgs = partStore.getUploadPart(uploadMultipartResponse.getUploadId(), objectName, uploadMultipartResponse.getPartNumber());
//                try {
//                    long chunkSize = 1L << 32;
//                    if (uploadPartArgs.getPartSize() >= chunkSize) {
//                        throw new IllegalArgumentException("文件分片必须<4G");
//                    }
//                    try (FileChannel inChannel = ((FileInputStream) uploadPartArgs.getInputStream()).getChannel()) {
//                        int position = 0;
//                        long size = inChannel.size();
//                        while (0 < size) {
//                            long count = inChannel.transferTo(position, size, outChannel);
//                            if (count > 0) {
//                                position += count;
//                                size -= count;
//                            }
//                        }
//                    }
//                } finally {
//                    Util.close(uploadPartArgs.getInputStream());
//                }
//
//            }
//        } catch (IOException e) {
//            throw new FileIOException("local complete file error", e);
//        }
//        CompleteMultipartResponse completeMultipartResponse = new CompleteMultipartResponse();
//        completeMultipartResponse.setObjectName(objectName);
//        completeMultipartResponse.setETag(Util.eTag(outFile));
//        return completeMultipartResponse;
//    }
//
//    @Override
//    public void abortMultipartUpload(String uploadId, String objectName) {
//        //TODO abortMultipartUpload
//    }
//
//    @Override
//    public int getPartExpirationDays() {
//        //不删除策略
//        return -1;
//    }
//
//}
