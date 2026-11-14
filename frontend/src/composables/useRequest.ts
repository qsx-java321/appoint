import { ref, shallowRef } from "vue";
import { message } from "../api/client";

export function useRequest<T>(initial: T) {
  const data = shallowRef<T>(initial);
  const loading = ref(false);
  const error = ref("");
  let version = 0;
  async function run(load: () => Promise<T>) {
    const current = ++version;
    loading.value = true;
    error.value = "";
    try {
      const result = await load();
      if (current === version) data.value = result;
    } catch (e) {
      if (current === version) error.value = message(e);
    } finally {
      if (current === version) loading.value = false;
    }
  }
  function invalidate() {
    ++version;
    loading.value = false;
    error.value = "";
  }
  return { data, loading, error, run, invalidate };
}
