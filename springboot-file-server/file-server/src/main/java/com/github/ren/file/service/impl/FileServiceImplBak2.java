//package com.github.ren.file.server.service.impl;
//
//import cn.hutool.core.bean.BeanUtil;
//import com.github.ren.file.client.FileClient;
//import com.github.ren.file.client.model.UploadGenericResult;
//import com.github.ren.file.server.client.objectname.UuidGenerator;
//import com.github.ren.file.client.part.*;
//import com.github.ren.file.server.config.FileUploadProperties;
//import com.github.ren.file.server.entity.TFile;
//import com.github.ren.file.server.entity.TUpload;
//import com.github.ren.file.server.ex.ApiException;
//import com.github.ren.file.server.model.ErrorCode;
//import com.github.ren.file.server.model.request.*;
//import com.github.ren.file.server.model.result.CheckResult;
//import com.github.ren.file.server.model.result.InitPartResult;
//import com.github.ren.file.server.model.result.PartResult;
//import com.github.ren.file.server.service.FileService;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.io.FilenameUtils;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.IOException;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * @Description 文件接口逻辑类
// * @Author ren
// * @Since 1.0
// */
//@Service
//@Slf4j
//public class FileServiceImpl implements FileService {
//
//    @Autowired
//    private FileClient fileClient;
//
//    @Autowired
//    private TFileServiceImpl tFileService;
//
//    @Autowired
//    private TUploadServiceImpl tUploadService;
//
//    private long maxUploadSize = 1024 * 1024 * 10;
//
//    private long multipartMinSize = 1024 * 100;
//
//    private long multipartMaxSize = 1024 * 1024 * 100;
//
//    private final String storage;
//
//    private final int storageTypeInt;
//
//    public FileServiceImpl(FileUploadProperties fileUploadProperties) {
//        long maxUploadSize = fileUploadProperties.getMaxUploadSize().toBytes();
//        if (maxUploadSize != 0) {
//            this.maxUploadSize = multipartMaxSize;
//        }
//
//        long multipartMaxSize = fileUploadProperties.getMultipartMaxSize().toBytes();
//        if (multipartMaxSize > 1) {
//            this.multipartMaxSize = multipartMaxSize;
//        }
//
//        long multipartMinSize = fileUploadProperties.getMultipartMinSize().toBytes();
//        if (multipartMinSize != 0) {
//            this.multipartMinSize = multipartMinSize;
//        }
//
//        this.multipartMinSize = fileUploadProperties.getMinPartSize();
//        this.storage = fileUploadProperties.getStorageTypeName();
//        this.storageTypeInt = fileUploadProperties.getStorageTypeInt();
//    }
//
//    public String objectName(String filename) {
//        return new UuidGenerator(filename).generator();
//    }
//
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public String upload(SimpleUploadRequest request) {
//        MultipartFile file = request.getFile();
//        if (file.getSize() > maxUploadSize) {
//            throw new ApiException(ErrorCode.FILE_TO_LARGE);
//        }
//        try {
//            String objectName = objectName(file.getOriginalFilename());
//            UploadGenericResult result = fileClient.upload(file.getInputStream(), objectName);
//            objectName = result.getObjectName();
//            return objectName;
//        } catch (IOException e) {
//            log.error("上传失败", e);
//            throw new ApiException(ErrorCode.UPLOAD_ERROR);
//        }
//    }
//
//    private TUpload checkUploadId(String uploadId) {
//        TUpload tUpload = tUploadService.selectUpload(uploadId);
//        if (tUpload == null || tUpload.getStatus() != 0) {
//            throw new ApiException(ErrorCode.UPLOAD_ID_NOT_FOUND);
//        }
//        LocalDateTime expireAt = tUpload.getExpireAt();
//        if (expireAt != null && LocalDateTime.now().isAfter(expireAt)) {
//            throw new ApiException(ErrorCode.UPLOAD_ID_EXPIRE);
//        }
//        return tUpload;
//    }
//
//    private String getObjectName(String uploadId) {
//        TUpload tUpload = checkUploadId(uploadId);
//        return tUpload.getObjectName();
//    }
//
//    private int getMaxPartCount(long filesize, long partSize) {
//        return Math.max(1, (int) Math.ceil(filesize / (float) partSize));
//    }
//
//    private TUpload checkMultipart(UploadPartRequest uploadPartRequest) {
//        MultipartFile file = uploadPartRequest.getFile();
//        String uploadId = uploadPartRequest.getUploadId();
//        TUpload tUpload = checkUploadId(uploadId);
//        Integer partNumber = uploadPartRequest.getPartNumber();
//        long currentSize = file.getSize();
//
//        //判断分片大小
//        if (currentSize > multipartMaxSize) {
//            throw new ApiException(ErrorCode.FILE_PART_SIZE_ERROR, "必须小于" + multipartMaxSize / 1024 / 1024 + "M");
//        }
//        Long partSize = tUpload.getPartSize();
//        Long fileSize = tUpload.getFileSize();
//
//        int maxPartCount = getMaxPartCount(fileSize, partSize);
//
//        boolean lastPart = false;
//
//        //最后一个分片 如果实际分片size>partSize 则partNumber = maxPartCount-1
//        if (currentSize > partSize) {
//
//            if (partNumber != maxPartCount - 1) {
//                throw new ApiException(ErrorCode.FILE_PART_COUNT_ERROR);
//            }
//
//            if (fileSize != partSize * (partNumber - 1) + currentSize) {
//                throw new ApiException(ErrorCode.FILE_PART_TOTAL_SIZE_ERROR);
//            }
//
//            lastPart = true;
//        }
//        //最后一个分片 如果实际分片size<partSize 则partNumber = maxPartCount
//        if (currentSize < partSize) {
//            if (partNumber != maxPartCount) {
//                throw new ApiException(ErrorCode.FILE_PART_COUNT_ERROR);
//            }
//
//            lastPart = true;
//        }
//
//        //文件大小是分片大小的整数倍
//        if (currentSize == partSize && partNumber == maxPartCount) {
//            lastPart = true;
//        }
//
//        if (lastPart) {
////            throw new ApiException(ErrorCode.FILE_PART_COUNT_ERROR);
//        }
//        return tUpload;
//    }
//
//    private List<UploadMultipartResponse> getMultiParts(String uploadId, String objectName) {
//        List<UploadMultipartResponse> listMultipart = tUploadService.listMultipart(uploadId, objectName);
//
//        if (listMultipart.isEmpty()) {
//            ListMultipartUploadArgs args = ListMultipartUploadArgs.builder().uploadId(uploadId).objectName(objectName).build();
//            listMultipart = fileClient.listMultipartUpload(args);
//        }
//
//        return listMultipart;
//    }
//
//    @Override
//    public CheckResult check(CheckRequest request) {
//        CheckResult checkResult = new CheckResult();
////        String md5 = request.getMd5();
////        if (StringUtils.isNotBlank(md5)) {
////        checkResult.setExist(false);
////        }
//        String uploadId = request.getUploadId();
//        String objectName = getObjectName(uploadId);
//        List<UploadMultipartResponse> multiParts = getMultiParts(uploadId, objectName);
//        List<PartResult> partResults = new ArrayList<>(multiParts.size());
//        for (UploadMultipartResponse multiPart : multiParts) {
//            PartResult partResult = BeanUtil.toBean(multiPart, PartResult.class);
//            partResults.add(partResult);
//        }
//        checkResult.setParts(partResults);
//        return checkResult;
//    }
//
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public InitPartResult initMultipart(InitPartRequest request) {
//        String filename = request.getFilename();
//        Long filesize = request.getFilesize();
//        Long partsize = request.getPartsize();
//        if (partsize > multipartMaxSize) {
//            throw new ApiException(ErrorCode.FILE_PART_SIZE_ERROR, "必须小于" + multipartMaxSize / 1024 / 1024 + "M");
//        }
//
//        String objectName = this.objectName(FilenameUtils.getExtension(filename));
//        InitMultipartUploadArgs args = InitMultipartUploadArgs.builder()
//                .objectName(objectName)
//                .fileSize(filesize).build();
//        InitMultipartResponse initMultipartResponse = fileClient.initMultipartUpload(args);
//        String uploadId = initMultipartResponse.getUploadId();
//        objectName = initMultipartResponse.getObjectName();
//
//        int partExpirationDays = fileClient.getPartExpirationDays();
//
//        TUpload tUpload = new TUpload();
//        tUpload.setUploadId(uploadId);
//        tUpload.setObjectName(objectName);
//        tUpload.setCreateTime(LocalDateTime.now());
//
////        tUpload.setExpireAt(expire);
//        tUpload.setStorage(storageTypeInt);
//        tUpload.setFileSize(filesize);
//        tUpload.setPartSize(partsize);
//        tUpload.setStatus(0);
//
//        tUploadService.save(tUpload);
//
//        InitPartResult initPartResult = new InitPartResult();
//        initPartResult.setUploadId(uploadId);
//        return initPartResult;
//    }
//
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public PartResult uploadMultipart(UploadPartRequest uploadPartRequest) {
//        TUpload tUpload = checkMultipart(uploadPartRequest);
//        String uploadId = tUpload.getUploadId();
//        String objectName = tUpload.getObjectName();
//        Integer partNumber = uploadPartRequest.getPartNumber();
//        Long fileSize = tUpload.getFileSize();
//        MultipartFile file = uploadPartRequest.getFile();
//        long currentSize = file.getSize();
//        try {
//            UploadPartArgs part = new UploadPartArgs(uploadId, objectName, partNumber, fileSize, currentSize, file.getInputStream());
//            UploadMultipartResponse uploadMultipartResponse = fileClient.uploadMultipart(part);
//            tUploadService.saveMultipart(uploadMultipartResponse);
//            return BeanUtil.toBean(uploadMultipartResponse, PartResult.class);
//        } catch (IOException e) {
//            log.error("分片上传失败", e);
//            throw new ApiException(ErrorCode.UPLOAD_ERROR);
//        }
//    }
//
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public CompleteMultipartResponse completeMultipart(CompletePartRequest request) {
//        String uploadId = request.getUploadId();
//        TUpload tUpload = checkUploadId(uploadId);
//        String objectName = getObjectName(uploadId);
//        List<UploadMultipartResponse> multiParts = getMultiParts(uploadId, objectName);
//
//        Long fileSize = tUpload.getFileSize();
//        Long partSize = tUpload.getPartSize();
//        int maxPartCount = getMaxPartCount(fileSize, partSize);
//        //判断分片数量是否有误
//        int size = multiParts.size();
//        if (size != maxPartCount - 1 && size != maxPartCount) {
//            throw new ApiException(ErrorCode.FILE_PART_COUNT_ERROR);
//        }
//
//        //判断分片总大小是否有误
//        long totalSize = multiParts.stream()
//                .mapToLong(UploadMultipartResponse::getPartSize)
//                .sum();
//        if (fileSize != totalSize) {
//            throw new ApiException(ErrorCode.FILE_PART_TOTAL_SIZE_ERROR);
//        }
//
//        CompleteMultipartResponse completeMultipartResponse = fileClient.completeMultipartUpload(uploadId, objectName, multiParts);
//        tUploadService.completeUpload(uploadId);
//        log.info("完成上传 uploadId={} objectName={} result={}", uploadId, objectName, completeMultipartResponse);
//        TFile tFile = new TFile();
//        tFile.setFilesize(fileSize);
//        tFile.setCreateTime(LocalDateTime.now());
//        tFile.setEtag(completeMultipartResponse.getETag());
//        tFile.setObjectName(completeMultipartResponse.getObjectName());
//        tFile.setMd5(request.getMd5());
//        tFile.setStatus(1);
//        tFileService.save(tFile);
//        return completeMultipartResponse;
//    }
//
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public void abortMultipart(AbortPartRequest request) {
//        String uploadId = request.getUploadId();
//        String objectName = getObjectName(uploadId);
//        fileClient.abortMultipartUpload(uploadId, objectName);
//        tUploadService.abortUpload(uploadId);
//    }
//}
