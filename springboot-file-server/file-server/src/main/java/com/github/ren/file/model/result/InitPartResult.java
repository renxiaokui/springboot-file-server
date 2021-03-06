package com.github.ren.file.model.result;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * @Description 初始化分片上传结果
 * @Author ren
 * @Since 1.0
 */
@Data
@ApiModel("初始化分片上传结果")
public class InitPartResult implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(name = "uploadId", value = "分片上传唯一标识")
    private String uploadId;
}
