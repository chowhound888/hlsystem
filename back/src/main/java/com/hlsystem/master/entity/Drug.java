package com.hlsystem.master.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

@Data
@TableName("md_drug")
public class Drug {
  private Long id;
  private Long itemId;
  private String genericName;
  private String tradeName;
  private Long manufacturerId;
  private String dosageForm;
  private String category;
  private String insuranceCode;
  private String insuranceCategory;
  private Boolean prescriptionRequired;
  private String controlLevel;
  private Boolean imported;
  private Boolean inHouse;
  private Boolean selfPay;
  private Boolean valuable;
  private BigDecimal retailPrice;
  private Integer priceVersion;
}
