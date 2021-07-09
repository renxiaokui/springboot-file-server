package com.github.ren.file.server.client;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.github.ren.file.client.fdfs.FastDfsStorageClient;
import com.github.ren.file.client.fdfs.FastPart;
import com.github.ren.file.client.starter.StorageProperties;
import com.github.ren.file.client.starter.StorageType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author RenYinKui
 * @Description:
 * @date 2021/7/9 10:46
 */
@Service
@Slf4j
public class MultipartUploadAdapter implements FileClientService {

    @Autowired(required = false)
    private FastDfsStorageClient fastDfsStorageClient;

    @Autowired(required = false)
    private AmazonS3 amazonS3;

    private String bucketName;

    public MultipartUploadAdapter(StorageProperties storageProperties) {
        this.bucketName = storageProperties.getBucketName();
    }

    private void assertParameterNotNull(Object parameterValue, String errorMessage) {
        if (parameterValue == null) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    @Override
    public InitMultipartResponse initMultipartUpload(StorageType storage, InitMultipartUploadArgs args) {
        assertParameterNotNull(storage, "文件服务类型不能为空");
        assertParameterNotNull(args, "args不能为空");
        String objectName = args.getObjectName();
        assertParameterNotNull(objectName, "参数objectName不能为空");
        if (StorageType.FastDfs.equals(storage)) {
            Long fileSize = args.getFileSize();
            Long partSize = args.getPartSize();
            assertParameterNotNull(fileSize, "fastdfs服务文件大小fileSize不能为空");
            assertParameterNotNull(fileSize, "fastdfs服务文件分片大小partSize不能为空");
            assertParameterNotNull(fileSize, "fastdfs服务文件分片大小partSize不能为空");
            String filePath = fastDfsStorageClient.initiateMultipartUpload(fileSize, partSize, FilenameUtils.getExtension(objectName));
            return new InitMultipartResponse(null, filePath);
        }

        if (StorageType.S3.equals(storage)) {
            assertParameterNotNull(bucketName, "参数bucketName不能为空");
            InitiateMultipartUploadRequest initiateMultipartUploadRequest = new InitiateMultipartUploadRequest(bucketName, objectName);
            InitiateMultipartUploadResult initiateMultipartUploadResult = amazonS3.initiateMultipartUpload(initiateMultipartUploadRequest);
            return new InitMultipartResponse(initiateMultipartUploadResult.getUploadId(), initiateMultipartUploadResult.getKey());
        }

        throw new IllegalStateException("文件服务未找到");
    }

    @Override
    public PartResult uploadMultipart(StorageType storage, UploadMultiPartArgs args) {
        assertParameterNotNull(storage, "文件服务类型不能为空");
        assertParameterNotNull(args, "args不能为空");
        String objectName = args.getObjectName();
        Integer partNumber = args.getPartNumber();
        InputStream inputStream = args.getInputStream();
        assertParameterNotNull(objectName, "objectName不能为空");
        assertParameterNotNull(partNumber, "partNumber不能为空");
        assertParameterNotNull(inputStream, "inputStream不能为空");

        if (StorageType.FastDfs.equals(storage)) {
            FastPart fastPart = fastDfsStorageClient.uploadPart(objectName, partNumber, inputStream);
            PartResult partResult = new PartResult();
            partResult.setPartNumber(fastPart.getPartNumber());
            partResult.setPartSize(fastPart.getPartSize());
            return partResult;
        }

        if (StorageType.S3.equals(storage)) {
            String uploadId = args.getUploadId();
            Long partSize = args.getPartSize();
            assertParameterNotNull(uploadId, "uploadId不能为空");
            assertParameterNotNull(partSize, "partSize不能为空");
            com.amazonaws.services.s3.model.UploadPartRequest uploadRequest =
                    new com.amazonaws.services.s3.model.UploadPartRequest()
                            .withBucketName(bucketName)
                            .withKey(objectName)
                            .withUploadId(uploadId)
                            .withPartNumber(partNumber)
                            .withInputStream(inputStream)
                            .withPartSize(partSize);
            UploadPartResult uploadPartResult = amazonS3.uploadPart(uploadRequest);
            PartResult partResult = new PartResult();
            partResult.setPartNumber(uploadPartResult.getPartNumber());
            partResult.setPartSize(partSize);
            partResult.setETag(uploadPartResult.getETag());
            return partResult;
        }
        throw new IllegalStateException("文件服务未找到");
    }

    @Override
    public List<PartResult> listParts(StorageType storage, ListPartsArgs args) {
        assertParameterNotNull(storage, "文件服务类型不能为空");
        assertParameterNotNull(args, "args不能为空");
        String objectName = args.getObjectName();
        assertParameterNotNull(objectName, "objectName不能为空");

        if (StorageType.FastDfs.equals(storage)) {
            List<FastPart> fastParts = fastDfsStorageClient.listParts(objectName);
            List<PartResult> list = new ArrayList<>(fastParts.size());
            for (FastPart fastPart : fastParts) {
                PartResult partResult = new PartResult();
                partResult.setPartNumber(fastPart.getPartNumber());
                partResult.setPartSize(fastPart.getPartSize());
                list.add(partResult);
            }
            return list;
        }
        if (StorageType.S3.equals(storage)) {
            String uploadId = args.getUploadId();
            assertParameterNotNull(objectName, "uploadId不能为空");
            List<PartResult> list = new ArrayList<>();
            ListPartsRequest listPartsRequest = new ListPartsRequest(bucketName, objectName, uploadId);
            PartListing partListing;
            do {
                partListing = amazonS3.listParts(listPartsRequest);
                for (PartSummary part : partListing.getParts()) {
                    PartResult partResult = new PartResult();
                    partResult.setPartNumber(part.getPartNumber());
                    partResult.setPartSize(part.getSize());
                    partResult.setETag(part.getETag());
                    list.add(partResult);
                }
                listPartsRequest.setPartNumberMarker(partListing.getNextPartNumberMarker());
            } while (partListing.isTruncated());
            return list;
        }
        throw new IllegalStateException("文件服务未找到");
    }

    @Override
    public CompleteMultipartResponse completeMultipartUpload(StorageType storage, CompleteMultiPartArgs args) {
        assertParameterNotNull(storage, "文件服务类型不能为空");
        assertParameterNotNull(args, "args不能为空");
        String objectName = args.getObjectName();
        assertParameterNotNull(objectName, "objectName不能为空");
        if (StorageType.FastDfs.equals(storage)) {
            String filePath = fastDfsStorageClient.completeMultipartUpload(objectName);
            return new CompleteMultipartResponse(null, filePath);
        }
        if (StorageType.S3.equals(storage)) {
            List<PartResult> parts = args.getParts();
            String uploadId = args.getUploadId();
            assertParameterNotNull(parts, "parts不能为空");
            assertParameterNotNull(uploadId, "uploadId不能为空");
            List<PartETag> partETags = new ArrayList<>(parts.size());
            for (PartResult part : parts) {
                PartETag partETag = new PartETag(part.getPartNumber(), part.getETag());
                partETags.add(partETag);
            }
            CompleteMultipartUploadRequest completeMultipartUploadRequest = new CompleteMultipartUploadRequest();
            completeMultipartUploadRequest.setBucketName(bucketName);
            completeMultipartUploadRequest.setUploadId(uploadId);
            completeMultipartUploadRequest.setKey(objectName);
            completeMultipartUploadRequest.setPartETags(partETags);
            CompleteMultipartUploadResult completeMultipartUploadResult = amazonS3.completeMultipartUpload(completeMultipartUploadRequest);
            String key = completeMultipartUploadResult.getKey();
            String eTag = completeMultipartUploadResult.getETag();
            return new CompleteMultipartResponse(eTag, key);
        }

        throw new IllegalStateException("文件服务未找到");
    }

    @Override
    public void abortMultipartUpload(StorageType storage, AbortMultiPartArgs args) {
        assertParameterNotNull(storage, "文件服务类型不能为空");
        assertParameterNotNull(args, "args不能为空");
        String objectName = args.getObjectName();
        assertParameterNotNull(objectName, "objectName不能为空");
        if (StorageType.FastDfs.equals(storage)) {
            fastDfsStorageClient.abortMultipartUpload(objectName);
        } else {
            String uploadId = args.getUploadId();
            assertParameterNotNull(objectName, "uploadId不能为空");
            AbortMultipartUploadRequest abortMultipartUploadRequest = new AbortMultipartUploadRequest(bucketName, objectName, uploadId);
            amazonS3.abortMultipartUpload(abortMultipartUploadRequest);
        }
    }
}