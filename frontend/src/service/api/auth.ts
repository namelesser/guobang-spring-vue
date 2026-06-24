import { request } from '../request';

export function fetchLogin(password: string) {
  return request<Api.Auth.LoginResponse>({
    url: '/api/auth/login',
    method: 'post',
    data: { password },
    withCredentials: true
  });
}

export function fetchGetUserInfo() {
  return request<Api.Auth.UserInfo>({
    url: '/api/auth/me',
    method: 'get'
  });
}

export function fetchLogout() {
  return request<{ authenticated?: boolean }>({
    url: '/api/auth/logout',
    method: 'post'
  });
}
