import { request } from '../request';

export function fetchLogin(password: string) {
  return request<Api.Auth.LoginToken>({
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

export function fetchRefreshToken() {
  return Promise.resolve<{ data: Api.Auth.LoginToken | null; error: null }>({ data: null, error: null });
}
