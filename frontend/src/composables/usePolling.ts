import { onScopeDispose, ref, watch, type Ref } from "vue";

export function usePolling(
  enabled: Ref<boolean>,
  refresh: () => Promise<boolean>,
) {
  const exhausted = ref(false);
  let timer: ReturnType<typeof setTimeout> | undefined,
    count = 0,
    generation = 0,
    disposed = false;
  function stop() {
    ++generation;
    clearTimeout(timer);
  }
  function start() {
    stop();
    if (disposed || !enabled.value || document.hidden) return;
    count = 0;
    exhausted.value = false;
    schedule(generation);
  }
  function schedule(current: number) {
    timer = setTimeout(async () => {
      if (current !== generation || !enabled.value || document.hidden) return;
      ++count;
      const ok = await refresh();
      if (current !== generation || !ok || !enabled.value || document.hidden)
        return;
      if (count >= 30) {
        exhausted.value = true;
        return;
      }
      schedule(current);
    }, 2000);
  }
  function visibility() {
    if (document.hidden) stop();
    else if (enabled.value)
      void refresh().then((ok) => {
        if (ok) start();
      });
  }
  watch(enabled, (value) => (value ? start() : stop()), { immediate: true });
  document.addEventListener("visibilitychange", visibility);
  onScopeDispose(() => {
    disposed = true;
    stop();
    document.removeEventListener("visibilitychange", visibility);
  });
  return { exhausted, restart: start };
}
