import { ref } from 'vue';

export function useAbortController() {
  const abortController = ref<AbortController | null>(null);

  function createController() {
    abort();
    abortController.value = new AbortController();
    return abortController.value;
  }

  function abort() {
    if (abortController.value) {
      abortController.value.abort();
      abortController.value = null;
    }
  }

  function getSignal(): AbortSignal | undefined {
    return abortController.value?.signal;
  }

  return {
    abortController,
    createController,
    abort,
    getSignal
  };
}
