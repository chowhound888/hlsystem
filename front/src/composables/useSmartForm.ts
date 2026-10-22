import { reactive, watch } from "vue";
import { useAuth } from "../stores/auth";

interface SmartFormOptions<T> {
  defaults: () => T;
  autoFillContext?: boolean;
  watchers?: Record<string, (value: any, form: T) => void | Promise<void>>;
}

/**
 * 智能表单 Hook
 */
export function useSmartForm<T extends Record<string, any>>(
  options: SmartFormOptions<T>
) {
  const { defaults, autoFillContext = true, watchers = {} } = options;
  const auth = useAuth();

  const form = reactive<T>(defaults()) as T;
  const originalValues = JSON.stringify(defaults());

  function isDirty(): boolean {
    return JSON.stringify(form) !== originalValues;
  }

  function reset(): void {
    Object.assign(form, defaults());
  }

  function fillContext(): void {
    if (!autoFillContext) return;
    const now = new Date();
    const today = now.toISOString().slice(0, 10);
    const contextFields: Record<string, any> = {
      locationId: auth.locationId,
      operatorId: auth.user?.id,
      businessDate: today,
      createdAt: now.toISOString(),
    };
    for (const [field, value] of Object.entries(contextFields)) {
      if (field in form && !(form as any)[field]) {
        (form as any)[field] = value;
      }
    }
  }

  function setupWatchers(): void {
    for (const [field, handler] of Object.entries(watchers)) {
      watch(
        () => (form as any)[field],
        async (newValue) => {
          try {
            await handler(newValue, form);
          } catch (e) {
            console.warn(`Watcher for ${field} failed:`, e);
          }
        }
      );
    }
  }

  fillContext();
  setupWatchers();

  return { form, reset, isDirty, fillContext };
}

interface ValidationRule {
  required?: boolean;
  min?: number;
  max?: number;
  pattern?: RegExp;
  validator?: (value: any, form: any) => string | null;
  message?: string;
}

/**
 * 实时表单验证 Hook
 */
export function useRealtimeValidation<T extends Record<string, any>>(
  form: T,
  rules: Record<string, ValidationRule[]>
) {
  const errors = reactive<Record<string, string[]>>({});
  const touched = reactive<Record<string, boolean>>({});

  function validateField(field: string): boolean {
    const fieldRules = rules[field];
    if (!fieldRules) return true;

    const value = (form as any)[field];
    const fieldErrors: string[] = [];

    for (const rule of fieldRules) {
      if (rule.required && !value && value !== 0) {
        fieldErrors.push(rule.message || "此字段为必填项");
        continue;
      }
      if (!value && value !== 0) continue;
      if (rule.min !== undefined && Number(value) < rule.min) {
        fieldErrors.push(rule.message || `不能小于 ${rule.min}`);
      }
      if (rule.max !== undefined && Number(value) > rule.max) {
        fieldErrors.push(rule.message || `不能大于 ${rule.max}`);
      }
      if (rule.pattern && !rule.pattern.test(String(value))) {
        fieldErrors.push(rule.message || "格式不正确");
      }
      if (rule.validator) {
        const err = rule.validator(value, form);
        if (err) fieldErrors.push(err);
      }
    }

    errors[field] = fieldErrors;
    return fieldErrors.length === 0;
  }

  function validateAll(): boolean {
    let isValid = true;
    for (const field of Object.keys(rules)) {
      touched[field] = true;
      if (!validateField(field)) isValid = false;
    }
    return isValid;
  }

  function touchField(field: string): void {
    touched[field] = true;
    validateField(field);
  }

  function clearErrors(): void {
    for (const field of Object.keys(errors)) errors[field] = [];
    for (const field of Object.keys(touched)) touched[field] = false;
  }

  function getFirstErrorField(): string | null {
    for (const [field, fieldErrors] of Object.entries(errors)) {
      if (fieldErrors.length > 0) return field;
    }
    return null;
  }

  function scrollToFirstError(): void {
    const field = getFirstErrorField();
    if (field) {
      const el = document.querySelector(`[data-field="${field}"]`);
      el?.scrollIntoView({ behavior: "smooth", block: "center" });
    }
  }

  return {
    errors,
    touched,
    validateField,
    validateAll,
    touchField,
    clearErrors,
    getFirstErrorField,
    scrollToFirstError,
  };
}
