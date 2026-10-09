import { defineStore } from "pinia";
import { ref } from "vue";
import { client, get, refreshCsrf, post } from "../api/client";
export interface User {
  id: string;
  username: string;
  displayName: string;
  roles: string[];
  permissions: string[];
  locations: { id: string; name: string; type: string }[];
}
export const useAuth = defineStore("auth", () => {
  const user = ref<User | null>(null),
    locationId = ref(localStorage.getItem("locationId") ?? "");
  async function load() {
    user.value = await get<User>("/auth/me");
    if (!user.value.locations.some((l) => l.id === locationId.value))
      locationId.value = user.value.locations[0]?.id ?? "";
  }
  async function login(username: string, password: string) {
    await refreshCsrf();
    await client.post(
      "/auth/login",
      new URLSearchParams({ username, password }),
    );
    await refreshCsrf();
    await load();
  }
  async function logout() {
    await post("/auth/logout");
    user.value = null;
    await refreshCsrf();
  }
  function can(p: string) {
    return user.value?.permissions.includes(p) ?? false;
  }
  function changeLocation(id: string) {
    locationId.value = id;
    localStorage.setItem("locationId", id);
  }
  return { user, locationId, load, login, logout, can, changeLocation };
});
