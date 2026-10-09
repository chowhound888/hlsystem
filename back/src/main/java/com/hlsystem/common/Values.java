package com.hlsystem.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public final class Values {
  private Values() {}

  public static long id(Object value) {
    if (value == null) throw new BusinessException("VALIDATION_FAILED", "缺少关联编号");
    return Long.parseLong(value.toString());
  }

  public static BigDecimal decimal(Object value) {
    return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
  }

  public static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  public static String text(Map<String, ?> map, String key) {
    return map.get(key) == null ? "" : map.get(key).toString();
  }

  public static void positive(BigDecimal value, String label) {
    if (value.signum() <= 0 || value.scale() > 6 || value.precision() - value.scale() > 12)
      throw new BusinessException("VALIDATION_FAILED", label + "必须为有效正数，最多6位小数");
  }
}
