package com.hlsystem.master.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("md_item")
public class Item {
  private Long id;
  private String code;
  private String name;
  private String specification;
  private String itemType;
  private Long baseUnitId;
  private Integer quantityScale;
  private Boolean enabled;
  private Integer version;
}
