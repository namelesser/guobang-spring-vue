declare namespace Api {
  /**
   * namespace Auth
   *
   * backend api module: "auth"
   */
  namespace Auth {
    interface LoginToken {
      token: string;
      refreshToken: string;
    }

    interface UserInfo {
      authenticated?: boolean;
      userId: string;
      userName: string;
      roles: string[];
      buttons: string[];
    }
  }
}
