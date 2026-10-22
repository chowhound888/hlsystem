import { ElMessage, ElMessageBox } from "element-plus";
import { useRouter } from "vue-router";

// 错误类型定义
export interface ApiError {
  code?: string;
  message?: string;
  status?: number;
  details?: any;
  requiredPermission?: string;
}

// 库存不足明细
export interface StockShortageItem {
  itemId: string;
  itemName: string;
  required: number;
  available: number;
  unit: string;
}

/**
 * 分层错误处理 Hook
 * 根据错误类型提供不同的用户反馈
 */
export function useErrorHandler() {
  const router = useRouter();

  /**
   * 处理 API 错误
   */
  function handleError(error: any): void {
    const response = error?.response;
    const status = response?.status;
    const data = response?.data as ApiError | undefined;
    const code = data?.code;

    // 根据状态码和错误码分类处理
    switch (true) {
      // === 认证相关 ===
      case status === 401:
        handleUnauthorized();
        break;

      // === 权限相关 ===
      case status === 403:
        handleForbidden(data?.requiredPermission);
        break;

      // === 资源不存在 ===
      case status === 404 || code === "NOT_FOUND":
        handleNotFound(data?.message);
        break;

      // === 版本冲突 ===
      case code === "VERSION_CONFLICT":
        handleVersionConflict();
        break;

      // === 库存不足 ===
      case code === "STOCK_INSUFFICIENT":
        handleStockInsufficient(data?.details);
        break;

      // === 期间已结账 ===
      case code === "PERIOD_CLOSED":
        handlePeriodClosed();
        break;

      // === 盘点冻结 ===
      case code === "STOCK_FROZEN":
        handleStockFrozen();
        break;

      // === 独立审核要求 ===
      case code === "INDEPENDENT_REVIEW_REQUIRED":
        handleIndependentReview();
        break;

      // === 批次冲突 ===
      case code === "BATCH_CONFLICT":
        handleBatchConflict();
        break;

      // === 验证失败 ===
      case code === "VALIDATION_FAILED":
        handleValidationFailed(data?.message, data?.details);
        break;

      // === 约束冲突 ===
      case code === "CONSTRAINT_CONFLICT":
        handleConstraintConflict();
        break;

      // === 无效状态 ===
      case code === "INVALID_STATE":
        handleInvalidState(data?.message);
        break;

      // === 可重试冲突 ===
      case code === "RETRYABLE_CONFLICT":
        handleRetryableConflict();
        break;

      // === 网络错误 ===
      case !response:
        handleNetworkError();
        break;

      // === 服务器错误 ===
      case status && status >= 500:
        handleServerError();
        break;

      // === 默认处理 ===
      default:
        handleDefault(data?.message || "操作失败");
    }
  }

  // --- 具体错误处理方法 ---

  function handleUnauthorized(): void {
    ElMessageBox.confirm("登录会话已过期，请重新登录", "会话过期", {
      confirmButtonText: "重新登录",
      cancelButtonText: "取消",
      type: "warning",
    })
      .then(() => {
        router.push("/login");
      })
      .catch(() => {});
  }

  function handleForbidden(requiredPermission?: string): void {
    const msg = requiredPermission
      ? `权限不足：需要 "${requiredPermission}" 权限才能执行此操作`
      : "您没有执行此操作的权限";

    ElMessage({
      message: msg,
      type: "warning",
      duration: 5000,
      showClose: true,
    });
  }

  function handleNotFound(message?: string): void {
    ElMessage({
      message: message || "请求的数据不存在或已被删除",
      type: "info",
      duration: 4000,
    });
  }

  function handleVersionConflict(): void {
    ElMessageBox.confirm(
      "该数据已被其他用户修改，当前显示的内容可能不是最新的。是否刷新获取最新数据？",
      "数据冲突",
      {
        confirmButtonText: "刷新数据",
        cancelButtonText: "稍后再说",
        type: "warning",
      }
    )
      .then(() => {
        window.location.reload();
      })
      .catch(() => {});
  }

  function handleStockInsufficient(details?: StockShortageItem[]): void {
    if (details && Array.isArray(details) && details.length > 0) {
      const content = details
        .map(
          (item) =>
            `<div style="margin: 8px 0; padding: 8px; background: #fef3c7; border-radius: 4px;">
              <strong>${item.itemName}</strong><br/>
              需要: ${item.required} ${item.unit} | 可用: ${item.available} ${item.unit}
            </div>`
        )
        .join("");

      ElMessageBox.alert(
        `<p>以下药品库存不足，无法完成操作：</p>${content}`,
        "库存不足",
        {
          dangerouslyUseHTMLString: true,
          confirmButtonText: "知道了",
          type: "error",
        }
      );
    } else {
      ElMessage.error("药品库存不足，无法完成操作");
    }
  }

  function handlePeriodClosed(): void {
    ElMessage({
      message: "当前会计期间已结账，无法执行此操作。如需修改，请联系管理员反结账。",
      type: "warning",
      duration: 6000,
      showClose: true,
    });
  }

  function handleStockFrozen(): void {
    ElMessage({
      message: "该机构正在进行盘点，库存操作已临时冻结。请等待盘点完成后再试。",
      type: "warning",
      duration: 6000,
      showClose: true,
    });
  }

  function handleIndependentReview(): void {
    ElMessage({
      message: "根据双人复核规则，制单人与审核人不能是同一人。请由其他有权限的人员进行审核。",
      type: "warning",
      duration: 6000,
      showClose: true,
    });
  }

  function handleBatchConflict(): void {
    ElMessage({
      message: "同一厂家、同一批号的药品已存在，但生产日期或有效期不一致。请核实批次信息。",
      type: "error",
      duration: 6000,
      showClose: true,
    });
  }

  function handleValidationFailed(message?: string, details?: any): void {
    if (details && Array.isArray(details)) {
      // 字段级错误
      const errors = details
        .map((d: any) => `• ${d.field}: ${d.message}`)
        .join("<br/>");
      ElMessageBox.alert(
        `<p>请检查以下字段：</p><div style="text-align: left; margin-top: 8px;">${errors}</div>`,
        "表单验证失败",
        {
          dangerouslyUseHTMLString: true,
          confirmButtonText: "去修改",
          type: "warning",
        }
      );
    } else {
      ElMessage({
        message: message || "请检查填写内容是否符合要求",
        type: "warning",
        duration: 5000,
        showClose: true,
      });
    }
  }

  function handleConstraintConflict(): void {
    ElMessage({
      message: "数据冲突：编码重复、关联数据不存在或记录已被其他业务引用。",
      type: "error",
      duration: 5000,
      showClose: true,
    });
  }

  function handleInvalidState(message?: string): void {
    ElMessage({
      message: message || "当前状态不允许执行此操作，请刷新页面查看最新状态。",
      type: "warning",
      duration: 5000,
      showClose: true,
    });
  }

  function handleRetryableConflict(): void {
    ElMessage({
      message: "数据正在被其他操作更新，请稍后重试。",
      type: "info",
      duration: 4000,
    });
  }

  function handleNetworkError(): void {
    ElMessage({
      message: "网络连接失败，请检查网络后重试。",
      type: "error",
      duration: 5000,
      showClose: true,
    });
  }

  function handleServerError(): void {
    ElMessage({
      message: "服务器繁忙，请稍后重试。如问题持续，请联系技术支持。",
      type: "error",
      duration: 5000,
      showClose: true,
    });
  }

  function handleDefault(message: string): void {
    ElMessage({
      message,
      type: "error",
      duration: 5000,
      showClose: true,
    });
  }

  return {
    handleError,
  };
}
