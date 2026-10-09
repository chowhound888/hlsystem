package com.hlsystem.master.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record DrugRequest(
    @NotBlank @Size(max = 64) String code,
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 200) String specification,
    @NotNull Long baseUnitId,
    @NotNull Long manufacturerId,
    @NotBlank String dosageForm,
    @NotBlank String category,
    String tradeName,
    String insuranceCode,
    @NotBlank String insuranceCategory,
    @NotBlank String controlLevel,
    boolean prescriptionRequired,
    boolean imported,
    boolean inHouse,
    boolean selfPay,
    boolean valuable,
    @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 6) BigDecimal retailPrice,
    @Min(0) @Max(6) int quantityScale,
    Long packagingUnitId,
    @DecimalMin("0.000001") @Digits(integer = 12, fraction = 6) BigDecimal packagingFactor,
    List<@NotBlank @Size(max = 200) String> aliases,
    Integer version) {}
