import type { AxiosResponse } from 'axios';
import { BACKEND_ERROR_CODE, createFlatRequest } from '@sa/axios';
import { useAuthStore } from '@/store/modules/auth';
import { getServiceBaseURL } from '@/utils/service';
import { showErrorMsg } from './shared';
import type { RequestInstanceState } from './type';

const isHttpProxy = import.meta.env.DEV && import.meta.env.VITE_HTTP_PROXY === 'Y';
const { baseURL } = getServiceBaseURL(import.meta.env, isHttpProxy);

export const request = createFlatRequest(
  {
    baseURL,
    withCredentials: true,
    headers: {}
  },
  {
    defaultState: {
      errMsgStack: []
    } as RequestInstanceState,
    transform(response: AxiosResponse<any>) {
      return response.data;
    },
    async onRequest(config) {
      return config;
    },
    isBackendSuccess(response) {
      return response.data?.ok === true;
    },
    async onBackendFail(response) {
      const authStore = useAuthStore();

      // handle 401 Unauthorized - clear token and redirect to login
      if (response.status === 401) {
        authStore.resetStore();
      }

      return null;
    },
    onError(error) {
      // when the request is fail, you can show error message

      let message = error.message;
      // get backend error message and code
      if (error.code === BACKEND_ERROR_CODE) {
        message = error.response?.data?.error || error.response?.data?.msg || message;
      }

      showErrorMsg(request.state, message);
    }
  }
);
